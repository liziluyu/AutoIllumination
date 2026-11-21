package com.liziluyu.autoillumination.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.foundationgames.automobility.forge.vendored.jsonem.util.JsonEntityModelUtil;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.loading.object.BakedModelFactory;
import software.bernie.geckolib.loading.object.GeometryTree;
import software.bernie.geckolib.loading.json.raw.Model;
import software.bernie.geckolib.util.JsonUtil;
import software.bernie.geckolib.util.RenderUtils;

/** Discovers rectangular emitting faces in model bones whose names start with {@code headlight}. */
public final class HeadlightModelGeometry {
    private static final float EPSILON = 1.0e-4f;

    private HeadlightModelGeometry() {
    }

    /**
     * Extracts JsonEM geometry after the DynamicAutomobileModel Y rotation. The baked model's
     * visitor supplies the same accumulated transforms used for rendering.
     */
    public static List<FrameLightProfiles.Beam> jsonEm(JsonObject model, float rotationY) {
        if (model == null || !Float.isFinite(rotationY)) {
            return List.of();
        }
        byte[] json = model.toString().getBytes(StandardCharsets.UTF_8);
        LayerDefinition layer = JsonEntityModelUtil.readJson(new ByteArrayInputStream(json)).orElse(null);
        if (layer == null) {
            return List.of();
        }
        ModelPart main = layer.bakeRoot().getChild("main"); // BaseModel renders only this subtree.
        Map<String, List<String>> cubeNames = jsonEmCubeNames(model);
        List<FrameLightProfiles.Beam> out = new ArrayList<>();
        PoseStack stack = new PoseStack();
        stack.mulPose(Axis.YP.rotationDegrees(rotationY));
        main.visit(stack, (pose, path, cubeIndex, cube) -> {
            boolean namedPath = hasHeadlightSegment(path);
            List<String> names = cubeNames.get(path);
            boolean namedCube = names != null && cubeIndex < names.size()
                && startsHeadlight(names.get(cubeIndex));
            if (namedPath || namedCube) {
                Matrix4f sourceToBeam = new Matrix4f().rotateY((float) Math.PI).mul(pose.pose());
                emit(sourceToBeam, cube.minX, cube.minY, cube.minZ,
                    cube.maxX, cube.maxY, cube.maxZ, out);
            }
        });
        return List.copyOf(out);
    }

    /** Uses GeckoLib's own baker and renderer transforms, without modifying the live animated model. */
    public static List<FrameLightProfiles.Beam> gecko(JsonObject model) {
        if (model == null) {
            return List.of();
        }
        Model raw = JsonUtil.GEO_GSON.fromJson(model, Model.class);
        BakedGeoModel baked = BakedModelFactory.DEFAULT_FACTORY.constructGeoModel(GeometryTree.fromModel(raw));
        Map<String, List<String>> cubeNames = geckoCubeNames(model);
        List<FrameLightProfiles.Beam> out = new ArrayList<>();
        PoseStack stack = new PoseStack();
        stack.mulPose(Axis.YP.rotationDegrees(180f)); // beam basis
        stack.scale(-1f, -1f, 1f); // GeckoFrameModel.render
        for (GeoBone bone : baked.topLevelBones()) {
            collectBakedGecko(bone, stack, false, cubeNames, out);
        }
        return List.copyOf(out);
    }

    private static void collectBakedGecko(GeoBone bone, PoseStack stack, boolean inHeadlight,
                                          Map<String, List<String>> cubeNames,
                                          List<FrameLightProfiles.Beam> out) {
        stack.pushPose();
        try {
            RenderUtils.prepMatrixForBone(stack, bone);
            boolean selected = inHeadlight || startsHeadlight(bone.getName());
            List<String> names = cubeNames.getOrDefault(bone.getName(), List.of());
            for (int index = 0; index < bone.getCubes().size(); index++) {
                GeoCube cube = bone.getCubes().get(index);
                boolean cubeSelected = index < names.size() && startsHeadlight(names.get(index));
                if (!selected && !cubeSelected) continue;
                stack.pushPose();
                try {
                    RenderUtils.translateToPivotPoint(stack, cube);
                    RenderUtils.rotateMatrixAroundCube(stack, cube);
                    RenderUtils.translateAwayFromPivotPoint(stack, cube);
                    Matrix4f pose = new Matrix4f(stack.last().pose());
                    List<Vector3f> vertices = new ArrayList<>(24);
                    for (GeoQuad quad : cube.quads()) {
                        if (quad == null) continue;
                        for (GeoVertex vertex : quad.vertices())
                            vertices.add(new Vector3f(vertex.position()));
                    }
                    emitVertices(pose, vertices, out);
                } finally { stack.popPose(); }
            }
            for (GeoBone child : bone.getChildBones()) {
                collectBakedGecko(child, stack, selected, cubeNames, out);
            }
        } finally { stack.popPose(); }
    }

    private static Map<String, List<String>> geckoCubeNames(JsonObject model) {
        Map<String, List<String>> names = new HashMap<>();
        JsonArray geometries = array(model.get("minecraft:geometry"));
        JsonObject geometry = geometries != null && !geometries.isEmpty() ? object(geometries.get(0)) : model;
        JsonArray bones = array(geometry == null ? null : geometry.get("bones"));
        if (bones != null) for (JsonElement element : bones) {
            JsonObject bone = object(element);
            if (bone == null) continue;
            JsonArray cubes = array(bone.get("cubes"));
            if (cubes == null) continue;
            List<String> entries = new ArrayList<>();
            for (JsonElement cube : cubes) entries.add(string(object(cube) == null ? null : object(cube).get("name")));
            names.put(string(bone.get("name")), entries);
        }
        return names;
    }

    private static void emitVertices(Matrix4f pose, List<Vector3f> vertices, List<FrameLightProfiles.Beam> out) {
        if (vertices.size() < 4) return;
        // Baked vertices are axis-aligned in cube-local space; the pose contains cube/bone rotations.
        Vector3f[] lowHigh = bounds(vertices);
        emit(pose, lowHigh[0].x*16f, lowHigh[0].y*16f, lowHigh[0].z*16f,
            lowHigh[1].x*16f, lowHigh[1].y*16f, lowHigh[1].z*16f, out);
    }

    private static Vector3f[] bounds(List<Vector3f> vertices) {
        Vector3f lo = new Vector3f(Float.POSITIVE_INFINITY), hi = new Vector3f(Float.NEGATIVE_INFINITY);
        for (Vector3f p : vertices) { lo.min(p); hi.max(p); }
        return new Vector3f[]{lo, hi};
    }

    private static Map<String, List<String>> jsonEmCubeNames(JsonObject model) {
        Map<String, List<String>> result = new HashMap<>();
        JsonObject bones = object(model.get("bones"));
        if (bones == null) return result;
        for (var entry : bones.entrySet()) if (entry.getKey().equals("main"))
            collectJsonEmNames(object(entry.getValue()), "", result);
        return result;
    }

    private static void collectJsonEmNames(JsonObject bone, String path, Map<String, List<String>> out) {
        if (bone == null) return;
        JsonArray cuboids = array(bone.get("cuboids"));
        if (cuboids != null) {
            List<String> names = new ArrayList<>();
            for (JsonElement cuboid : cuboids) names.add(string(object(cuboid) == null
                ? null : object(cuboid).get("name")));
            out.put(path, names);
        }
        JsonObject children = object(bone.get("children"));
        if (children != null) for (var child : children.entrySet())
            collectJsonEmNames(object(child.getValue()), path + "/" + child.getKey(), out);
    }

    private static void emit(Matrix4f pose, float x0, float y0, float z0,
                             float x1, float y1, float z1, List<FrameLightProfiles.Beam> out) {
        float[] lo = {Math.min(x0,x1), Math.min(y0,y1), Math.min(z0,z1)};
        float[] hi = {Math.max(x0,x1), Math.max(y0,y1), Math.max(z0,z1)};
        float[] size = {hi[0]-lo[0], hi[1]-lo[1], hi[2]-lo[2]};
        int thin = 0;
        if (size[1] < size[thin]) thin = 1;
        if (size[2] < size[thin]) thin = 2;
        int widthAxis = thin == 0 ? 2 : 0;
        int heightAxis = thin == 1 ? 2 : 1;
        Vector3f right = new Vector3f();
        axis(right, widthAxis, 1f);
        pose.transformDirection(right);
        Vector3f down = new Vector3f();
        axis(down, heightAxis, 1f);
        pose.transformDirection(down);
        float width = size[widthAxis] * right.length();
        float height = size[heightAxis] * down.length();
        if (width <= EPSILON || height <= EPSILON) return;
        right.normalize();
        Vector3f center = new Vector3f((lo[0]+hi[0])/2f, (lo[1]+hi[1])/2f, (lo[2]+hi[2])/2f);
        Vector3f normal = new Vector3f(); axis(normal, thin, 1f);
        if (size[thin] > EPSILON) axis(center, thin, hi[thin]);
        Vector3f f = pose.transformDirection(normal, new Vector3f()).normalize();
        if (size[thin] <= EPSILON && f.z < 0f) f.negate();
        else if (f.z < 0f) {
            axis(center, thin, lo[thin]);
            f.negate();
        }
        // Bounds use model pixels, while baked poses and Gecko vertices use blocks.
        Vector3f c = pose.transformPosition(center.div(16f), new Vector3f());
        float yaw = (float)Math.atan2(f.x, f.z);
        float pitch = (float)Math.asin(Math.max(-1f, Math.min(1f, f.y)));
        Vector3f right0 = new Vector3f((float)Math.cos(yaw), 0f, -(float)Math.sin(yaw));
        Vector3f down0 = new Vector3f(f).cross(right0).normalize();
        float roll = (float)Math.atan2(right.dot(down0), right.dot(right0));
        out.add(new FrameLightProfiles.Beam(width, height, c.x * 16f, -c.y * 16f, c.z * 16f,
            (float)Math.toDegrees(yaw), (float)Math.toDegrees(pitch), (float)Math.toDegrees(roll)));
    }

    private static boolean hasHeadlightSegment(String path) {
        for (String segment : path.split("/")) if (startsHeadlight(segment)) return true;
        return false;
    }
    private static boolean startsHeadlight(String name) {
        return name != null && name.toLowerCase(Locale.ROOT).startsWith("headlight");
    }
    private static void axis(Vector3f v, int axis, float value) { if (axis == 0) v.x=value; else if (axis == 1) v.y=value; else v.z=value; }
    private static JsonObject object(JsonElement e) { return e != null && e.isJsonObject() ? e.getAsJsonObject() : null; }
    private static JsonArray array(JsonElement e) { return e != null && e.isJsonArray() ? e.getAsJsonArray() : null; }
    private static String string(JsonElement e) { return e != null && e.isJsonPrimitive() ? e.getAsString() : ""; }
}
