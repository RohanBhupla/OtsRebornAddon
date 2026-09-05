package net.rebornaddon.armor.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.model.PositionTextureVertex;
import net.minecraft.client.model.TexturedQuad;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.IResource;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.util.math.MathHelper;
import net.rebornaddon.armor.OtsutsukiArmorSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class OtsutsukiArmorModel extends ModelBiped {
    private static final Logger LOGGER = LogManager.getLogger("RebornAddon Armor");
    private static final Gson GSON = new Gson();
    private static final float DEGREES_TO_RADIANS = (float) Math.PI / 180.0F;
    private static final float TWO_PI = (float) (Math.PI * 2.0D);
    private static final float SHOULDER_OVERLAP = 0.28F;
    private static final float LONG_ROBE_HEM_Y = 25.20F;
    private static final float COVERED_BOOT_TOP_FRACTION = 0.68F;
    private static final float COVERED_BOOT_MAX_INFLATE = 0.45F;
    private static final float COVERED_BOOT_SIDE_TRAVEL = 0.52F;
    private static final float ROBE_STRIDE_SCALE = 0.52F;
    private static final float ROBE_STRIDE_LIMIT = 7.0F;
    private static final float ROBE_HEM_LIFT_SCALE = 0.06F;
    private static final float ISSHIKI_COLLAR_SIDE_FACTOR = 0.30F;
    private static final float ISSHIKI_COLLAR_BACK_BASE_FACTOR = 0.43F;
    private static final float ISSHIKI_COLLAR_BACK_DEPTH_FACTOR = 0.18F;
    private static final float ISSHIKI_COLLAR_FRONT_DROP = 1.28F;
    private static final float ISSHIKI_COLLAR_FRONT_DROP_START = 0.65F;
    private static final float ISSHIKI_COLLAR_OUTER_RADIUS = 0.74F;
    private static final float ISSHIKI_COLLAR_INNER_RADIUS = 0.84F;
    private static final float ISSHIKI_COLLAR_VERTICAL_RADIUS = 0.50F;

    private final OtsutsukiArmorSet armorSet;
    private final EntityEquipmentSlot slot;
    private final Map<String, ElementData> elements = new HashMap<>();
    private final Map<String, GroupData> groups = new HashMap<>();
    private final List<AnimatedPart> animatedParts = new ArrayList<>();
    private final List<OrbitalPart> orbitalParts = new ArrayList<>();
    private final List<HeadMotionPart> headMotionParts = new ArrayList<>();
    private final List<CircularRobeRenderer> robeRenderers = new ArrayList<>();
    private boolean coveredByLongRobe;
    private float coveredBootCenterY = 10.305F;
    private float coveredBootHalfHeight = 2.145F;

    private OtsutsukiArmorModel(OtsutsukiArmorSet armorSet, EntityEquipmentSlot slot) {
        super(0.0F, 0.0F, 64, 64);
        this.armorSet = armorSet;
        this.slot = slot;
        clearVanillaGeometry();
    }

    static ModelBiped load(OtsutsukiArmorSet armorSet, EntityEquipmentSlot slot) {
        boolean leggings = slot == EntityEquipmentSlot.LEGS;
        try (IResource resource = Minecraft.getMinecraft().getResourceManager()
                .getResource(armorSet.modelResource(leggings));
             Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            return load(armorSet, slot, reader);
        } catch (Exception exception) {
            LOGGER.error("Could not load the {} {} cosmetic armor model.",
                    armorSet.displayName(), slot.getName(), exception);
            return null;
        }
    }

    static OtsutsukiArmorModel load(EntityEquipmentSlot slot, Reader reader) {
        return load(null, slot, reader);
    }

    static OtsutsukiArmorModel load(OtsutsukiArmorSet armorSet,
                                    EntityEquipmentSlot slot, Reader reader) {
        OtsutsukiArmorModel model = new OtsutsukiArmorModel(armorSet, slot);
        ProjectData project = GSON.fromJson(reader, ProjectData.class);
        model.build(project);
        return model;
    }

    private void build(ProjectData project) {
        if (project == null || project.resolution == null || project.outliner == null) {
            throw new IllegalArgumentException("Armor model is missing required geometry data");
        }
        textureWidth = project.resolution.width;
        textureHeight = project.resolution.height;

        if (project.elements != null) {
            for (ElementData element : project.elements) {
                elements.put(element.uuid, element);
            }
        }
        if (project.groups != null) {
            for (GroupData group : project.groups) {
                groups.put(group.uuid, group);
            }
        }

        for (JsonElement node : project.outliner) {
            if (!node.isJsonObject()) {
                continue;
            }
            JsonObject groupNode = node.getAsJsonObject();
            GroupData group = groups.get(string(groupNode, "uuid"));
            ModelRenderer target = targetFor(group == null ? "" : group.name);
            if (group != null && target != null) {
                attachGroup(groupNode, group, target, group.origin, target);
            }
        }
        attachHeadAnchoredGeometry();
        attachCustomLowerRobe();
        configureOrbitalCenter();
    }

    private void attachHeadAnchoredGeometry() {
        if (slot != EntityEquipmentSlot.HEAD || armorSet != OtsutsukiArmorSet.KAGUYA) {
            return;
        }
        ElementData backHair = elementNamedOrNull("hairback");
        if (backHair != null) {
            attachElement(backHair, bipedHead, new float[]{0.0F, 24.0F, 0.0F}, bipedHead);
        }
    }

    private void attachGroup(JsonObject node, GroupData group, ModelRenderer parent,
                             float[] parentOrigin, ModelRenderer rootTarget) {
        ModelRenderer renderer = renderer(group.origin, parentOrigin, group.rotation);
        parent.addChild(renderer);
        JsonArray children = node.getAsJsonArray("children");
        if (children == null) {
            return;
        }
        for (JsonElement child : children) {
            if (child.isJsonPrimitive()) {
                ElementData element = elements.get(child.getAsString());
                if (element != null) {
                    attachElement(element, renderer, group.origin, rootTarget);
                }
            } else if (child.isJsonObject()) {
                JsonObject childNode = child.getAsJsonObject();
                GroupData childGroup = groups.get(string(childNode, "uuid"));
                if (childGroup != null) {
                    attachGroup(childNode, childGroup, renderer, group.origin, rootTarget);
                }
            }
        }
    }

    private void attachElement(ElementData element, ModelRenderer parent, float[] parentOrigin,
                               ModelRenderer rootTarget) {
        float[] origin = vector(element.origin);
        ModelRenderer renderer = renderer(origin, parentOrigin, element.rotation);
        float x = cubeX(origin[0], value(element.to, 0));
        float y = cubeY(origin[1], value(element.to, 1));
        float z = cubeZ(origin[2], value(element.from, 2));
        float width = value(element.to, 0) - value(element.from, 0);
        float height = value(element.to, 1) - value(element.from, 1);
        float depth = value(element.to, 2) - value(element.from, 2);
        String name = element.name == null ? "" : element.name.toLowerCase(Locale.ROOT);
        if (armorSet == OtsutsukiArmorSet.KAGUYA && name.equals("hairback")
                && rootTarget != bipedHead) {
            return;
        }
        if (usesCustomLowerRobe(name)) {
            return;
        }
        HeadMotionType motionType = headMotionType(name, height, value(element.to, 1));
        float inflate = adjustedInflate(armorSet, name, element.inflate, width, depth);
        ModelBox box;
        ModelBox detailBox = null;
        boolean orb = name.startsWith("orb");
        boolean halo = name.equals("halo");
        ModelRenderer geometryRenderer = renderer;
        if (slot == EntityEquipmentSlot.FEET && name.contains("boot")) {
            box = new AdaptiveBootBox(this, renderer, element,
                    x, y, z, width, height, depth, inflate);
        } else if (orb) {
            geometryRenderer = new ModelRenderer(this);
            geometryRenderer.setTextureSize((int) textureWidth, (int) textureHeight);
            geometryRenderer.setRotationPoint(centerPivot(x, width), centerPivot(y, height),
                    centerPivot(z, depth));
            box = new SmoothSphereBox(geometryRenderer,
                    centeredMinimum(width), centeredMinimum(height), centeredMinimum(depth),
                    width, height, depth, inflate, MeshStyle.ORB_BASE);
            ModelRenderer coreGlow = new EmissiveGlowRenderer(this);
            coreGlow.cubeList.add(new SmoothSphereBox(coreGlow,
                    centeredMinimum(width), centeredMinimum(height), centeredMinimum(depth),
                    width, height, depth, inflate + 0.035F, MeshStyle.ORB_CORE_GLOW)
                    .setBoxName(element.name + " Core Glow"));
            geometryRenderer.addChild(coreGlow);
            ModelRenderer rimGlow = new EmissiveGlowRenderer(this);
            rimGlow.cubeList.add(new SmoothSphereBox(rimGlow,
                    centeredMinimum(width), centeredMinimum(height), centeredMinimum(depth),
                    width, height, depth, inflate + 0.19F, MeshStyle.ORB_RIM_GLOW)
                    .setBoxName(element.name + " Rim Glow"));
            geometryRenderer.addChild(rimGlow);
        } else if (halo) {
            box = new SmoothHaloBox(renderer, x, y, z, width, height,
                    0.60F, 0.0F, MeshStyle.HALO_BASE);
            ModelRenderer coreGlow = new EmissiveGlowRenderer(this);
            coreGlow.cubeList.add(new SmoothHaloBox(coreGlow, x, y, z, width, height,
                    0.63F, 0.008F, MeshStyle.HALO_CORE_GLOW)
                    .setBoxName(element.name + " Core Glow"));
            renderer.addChild(coreGlow);
            ModelRenderer rimGlow = new EmissiveGlowRenderer(this,
                    centerPivot(x, width), centerPivot(y, height), z,
                    1.006F, 1.025F);
            rimGlow.cubeList.add(new SmoothHaloBox(rimGlow, x, y, z, width, height,
                    0.70F, 0.025F, MeshStyle.HALO_RIM_GLOW)
                    .setBoxName(element.name + " Rim Glow"));
            renderer.addChild(rimGlow);
        } else if (armorSet == OtsutsukiArmorSet.ISSHIKI
                && name.equals("chestplate top")) {
            box = new OpenRolledCollarBox(renderer, element,
                    x, y, z, width, height, depth);
        } else if (armorSet == OtsutsukiArmorSet.TONERI
                && name.equals("chestplate top back")) {
            box = new ToneriCollarPanelBox(renderer, element,
                    x, y, z, width, height);
        } else if (armorSet == OtsutsukiArmorSet.TONERI
                && name.equals("chestplate top right")) {
            box = new ToneriSashPanelBox(renderer, element,
                    x, y, z, height, depth);
        } else if (slot == EntityEquipmentSlot.CHEST
                && name.equals("chestplate")
                && armorSet == OtsutsukiArmorSet.HAGOROMO) {
            float lowerExpansion = 0.12F;
            box = new RobeBodiceBox(renderer, element,
                    y - inflate, y + height + inflate,
                    centerPivot(x, width), centerPivot(z, depth),
                    width * 0.5F + inflate, depth * 0.5F + inflate,
                    width * 0.5F + inflate + lowerExpansion,
                    depth * 0.5F + inflate + lowerExpansion);
        } else if (armorSet == OtsutsukiArmorSet.KAGUYA
                && slot == EntityEquipmentSlot.CHEST
                && name.endsWith("arm armor")) {
            boolean right = name.startsWith("right");
            float topMinX = right ? x + 0.85F : x - 1.15F;
            float topMaxX = right ? x + width + 1.15F : x + width - 0.85F;
            float cuffMinX = right ? x + 1.35F : x - 1.10F;
            float cuffMaxX = right ? x + width + 1.10F : x + width - 1.35F;
            box = new TexturedPrismBox(renderer, element,
                    y - SHOULDER_OVERLAP, y + height - 1.05F,
                    topMinX, topMaxX,
                    z - 0.05F, z + depth + 0.05F,
                    cuffMinX, cuffMaxX,
                    z + 0.20F, z + depth - 0.20F,
                    0.0F, 0.92F);
            detailBox = new TexturedPrismBox(renderer, element,
                    y + height - 1.30F, y + height + 0.05F,
                    cuffMinX - 0.10F, cuffMaxX + 0.10F,
                    z + 0.08F, z + depth - 0.08F,
                    cuffMinX, cuffMaxX,
                    z + 0.18F, z + depth - 0.18F,
                    0.88F, 1.0F);
        } else if (isPrimarySleeve(name)) {
            box = new TexturedPrismBox(renderer, element,
                    y - inflate - SHOULDER_OVERLAP, y + height + inflate,
                    x - inflate, x + width + inflate,
                    z - inflate, z + depth + inflate,
                    x - inflate, x + width + inflate,
                    z - inflate, z + depth + inflate);
        } else {
            box = new ModelBox(renderer,
                    floor(value(element.uvOffset, 0)), floor(value(element.uvOffset, 1)),
                    x, y, z, floor(width), floor(height), floor(depth),
                    inflate, element.mirror);
        }
        geometryRenderer.cubeList.add(box.setBoxName(element.name));
        if (detailBox != null) {
            geometryRenderer.cubeList.add(detailBox.setBoxName(element.name + " Cuff"));
        }
        if (geometryRenderer != renderer) {
            renderer.addChild(geometryRenderer);
        }
        if (motionType != null) {
            ModelRenderer anchor = motionAnchor(renderer, motionType,
                    x, y, z, width, height, depth);
            parent.addChild(anchor);
            headMotionParts.add(new HeadMotionPart(anchor,
                    rootTarget == bipedHead, motionType));
        } else {
            parent.addChild(renderer);
        }
        if (orb) {
            orbitalParts.add(new OrbitalPart(renderer, geometryRenderer, element.uuid));
        } else if (halo) {
            animatedParts.add(new AnimatedPart(renderer,
                    null, element.uuid));
        }
    }

    @Override
    public void setRotationAngles(float limbSwing, float limbSwingAmount, float ageInTicks,
                                  float netHeadYaw, float headPitch, float scaleFactor,
                                  Entity entityIn) {
        super.setRotationAngles(limbSwing, limbSwingAmount, ageInTicks,
                netHeadYaw, headPitch, scaleFactor, entityIn);
        coveredByLongRobe = slot == EntityEquipmentSlot.FEET
                && entityIn instanceof EntityLivingBase
                && RobeMovementCompatibility.wearsLongRobe((EntityLivingBase) entityIn);
        float rightLegPitch = bipedRightLeg.rotateAngleX;
        float leftLegPitch = bipedLeftLeg.rotateAngleX;
        if (slot == EntityEquipmentSlot.FEET && coveredByLongRobe) {
            positionBootUnderRobe(bipedRightLeg, bipedBody,
                    rightLegPitch, leftLegPitch,
                    RobeMovementCompatibility.RIGHT_LEG_X,
                    coveredBootCenterY, coveredBootHalfHeight);
            positionBootUnderRobe(bipedLeftLeg, bipedBody,
                    leftLegPitch, rightLegPitch,
                    RobeMovementCompatibility.LEFT_LEG_X,
                    coveredBootCenterY, coveredBootHalfHeight);
        }
        if (slot == EntityEquipmentSlot.LEGS
                && (armorSet == OtsutsukiArmorSet.KAGUYA
                || armorSet == OtsutsukiArmorSet.HAGOROMO)) {
            RobeMovementCompatibility.freezeLegGeometry(this);
        }
        for (HeadMotionPart part : headMotionParts) {
            part.animate(bipedHead.rotateAngleX, bipedHead.rotateAngleY);
        }
        for (AnimatedPart part : animatedParts) {
            part.animate(ageInTicks);
        }
        for (OrbitalPart part : orbitalParts) {
            part.animate(ageInTicks);
        }
        for (CircularRobeRenderer renderer : robeRenderers) {
            renderer.animate(rightLegPitch, leftLegPitch,
                    limbSwingAmount, ageInTicks);
        }
    }

    static void positionBootUnderRobe(ModelRenderer leg, ModelRenderer body,
                                      float sourceLegPitch, float oppositeLegPitch,
                                      float legX,
                                      float bootCenterY, float bootHalfHeight) {
        float bodyPitch = body.rotateAngleX;
        float targetY = robeBootTargetY(sourceLegPitch, bootHalfHeight);
        float targetZ = robeBootTargetZ(sourceLegPitch, oppositeLegPitch);
        float bodyCosine = MathHelper.cos(bodyPitch);
        float bodySine = MathHelper.sin(bodyPitch);
        float worldTargetY = body.rotationPointY
                + targetY * bodyCosine - targetZ * bodySine;
        float worldTargetZ = body.rotationPointZ
                + targetY * bodySine + targetZ * bodyCosine;
        float bootPitch = bodyPitch + robeBootSwing(sourceLegPitch);

        leg.rotateAngleX = bootPitch;
        leg.rotateAngleY = body.rotateAngleY;
        leg.rotateAngleZ = body.rotateAngleZ;
        leg.rotationPointX = legX;
        leg.rotationPointY = worldTargetY
                - bootCenterY * MathHelper.cos(bootPitch);
        leg.rotationPointZ = worldTargetZ
                - bootCenterY * MathHelper.sin(bootPitch);
        leg.offsetX = 0.0F;
        leg.offsetY = 0.0F;
        leg.offsetZ = 0.0F;
    }

    boolean hasRenderableGeometry() {
        return !robeRenderers.isEmpty()
                || geometryCount(bipedHead) + geometryCount(bipedBody)
                + geometryCount(bipedRightArm) + geometryCount(bipedLeftArm)
                + geometryCount(bipedRightLeg) + geometryCount(bipedLeftLeg) > 0;
    }

    private static int geometryCount(ModelRenderer renderer) {
        int count = renderer.cubeList.size();
        if (renderer.childModels != null) {
            for (ModelRenderer child : renderer.childModels) {
                count += geometryCount(child);
            }
        }
        return count;
    }

    private void configureOrbitalCenter() {
        if (orbitalParts.size() <= 1) {
            if (orbitalParts.size() == 1) {
                orbitalParts.get(0).setPath(Collections.singletonList(
                        orbitalParts.get(0).basePoint()), 0);
            }
            return;
        }
        float centerX = 0.0F;
        float centerY = 0.0F;
        for (OrbitalPart part : orbitalParts) {
            centerX += part.baseAbsoluteX;
            centerY += part.baseAbsoluteY;
        }
        centerX /= orbitalParts.size();
        centerY /= orbitalParts.size();
        final float pathCenterX = centerX;
        final float pathCenterY = centerY;
        Collections.sort(orbitalParts, (first, second) -> Float.compare(
                clockwiseAngle(first.baseAbsoluteX, first.baseAbsoluteY,
                        pathCenterX, pathCenterY),
                clockwiseAngle(second.baseAbsoluteX, second.baseAbsoluteY,
                        pathCenterX, pathCenterY)));
        List<OrbPathPoint> path = new ArrayList<>(orbitalParts.size());
        for (OrbitalPart part : orbitalParts) {
            path.add(part.basePoint());
        }
        path = Collections.unmodifiableList(path);
        for (int index = 0; index < orbitalParts.size(); index++) {
            orbitalParts.get(index).setPath(path, index);
        }
    }

    private boolean usesCustomLowerRobe(String name) {
        return slot == EntityEquipmentSlot.LEGS
                && ((armorSet == OtsutsukiArmorSet.KAGUYA && name.contains("leg armor"))
                || (armorSet == OtsutsukiArmorSet.HAGOROMO && name.startsWith("dress ")));
    }

    private void attachCustomLowerRobe() {
        if (slot != EntityEquipmentSlot.LEGS) {
            return;
        }
        CircularRobeRenderer renderer = null;
        if (armorSet == OtsutsukiArmorSet.KAGUYA) {
            ElementData right = elementNamed("right leg armor");
            ElementData left = elementNamed("left leg armor");
            renderer = new CircularRobeRenderer(this,
                    12.85F, 25.20F, 4.18F, 2.18F, 6.35F, 4.88F,
                    0.04F,
                    faces(insetFaceEnd(face(right, "north"), 1.05F),
                            face(right, "east"), face(right, "south"),
                            face(left, "west")));
        } else if (armorSet == OtsutsukiArmorSet.HAGOROMO) {
            ElementData front = elementNamed("dress front");
            ElementData back = elementNamed("dress back");
            ElementData right = elementNamed("dress right");
            ElementData left = elementNamed("dress left");
            renderer = new CircularRobeRenderer(this,
                    13.70F, 25.15F, 4.90F, 2.90F, 6.18F, 4.78F,
                    -1.0F,
                    faces(face(front, "north"), face(right, "east"),
                            face(back, "south"), face(left, "west")));
        }
        if (renderer != null) {
            robeRenderers.add(renderer);
            bipedBody.addChild(renderer);
        }
    }

    private ElementData elementNamed(String expectedName) {
        ElementData element = elementNamedOrNull(expectedName);
        if (element != null) {
            return element;
        }
        throw new IllegalArgumentException("Missing lower robe element " + expectedName);
    }

    private ElementData elementNamedOrNull(String expectedName) {
        for (ElementData element : elements.values()) {
            if (element.name != null && element.name.equalsIgnoreCase(expectedName)) {
                return element;
            }
        }
        return null;
    }

    private static FaceData face(ElementData element, String faceName) {
        FaceData face = element.faces == null ? null : element.faces.get(faceName);
        if (face == null || face.uv == null || face.uv.length < 4) {
            throw new IllegalArgumentException("Missing lower robe texture face " + faceName);
        }
        return face;
    }

    private static FaceData[] faces(FaceData front, FaceData right,
                                    FaceData back, FaceData left) {
        return new FaceData[]{front, right, back, left};
    }

    private static FaceData insetFaceEnd(FaceData source, float pixels) {
        FaceData inset = new FaceData();
        inset.uv = source.uv.clone();
        float direction = Math.signum(inset.uv[2] - inset.uv[0]);
        inset.uv[2] -= direction * pixels;
        return inset;
    }

    private ModelRenderer renderer(float[] origin, float[] parentOrigin, float[] rotation) {
        ModelRenderer renderer = new ModelRenderer(this);
        renderer.setTextureSize((int) textureWidth, (int) textureHeight);
        float[] pivot = vector(origin);
        float[] parentPivot = vector(parentOrigin);
        renderer.setRotationPoint(pivotX(pivot[0], parentPivot[0]),
                pivotY(pivot[1], parentPivot[1]), pivotZ(pivot[2], parentPivot[2]));
        renderer.rotateAngleX = rotationX(value(rotation, 0));
        renderer.rotateAngleY = rotationY(value(rotation, 1));
        renderer.rotateAngleZ = rotationZ(value(rotation, 2));
        return renderer;
    }

    private ModelRenderer motionAnchor(ModelRenderer renderer, HeadMotionType type,
                                       float x, float y, float z,
                                       float width, float height, float depth) {
        ModelRenderer anchor = new ModelRenderer(this);
        anchor.setTextureSize((int) textureWidth, (int) textureHeight);
        float anchorX = renderer.rotationPointX + x + width * 0.5F;
        float firstY = renderer.rotationPointY + y;
        float secondY = firstY + height;
        float anchorY = nearestAttachmentEdge(firstY, secondY);
        float firstZ = renderer.rotationPointZ + z;
        float secondZ = firstZ + depth;
        float anchorZ = nearestAttachmentEdge(firstZ, secondZ);
        anchor.setRotationPoint(anchorX, anchorY, anchorZ);
        renderer.rotationPointX -= anchorX;
        renderer.rotationPointY -= anchorY;
        renderer.rotationPointZ -= anchorZ;
        anchor.addChild(renderer);
        return anchor;
    }

    private static boolean isPrimarySleeve(String name) {
        return "right arm armor".equals(name) || "left arm armor".equals(name);
    }

    private ModelRenderer targetFor(String groupName) {
        String name = groupName == null ? "" : groupName.replace(" ", "").replace("_", "")
                .toLowerCase(Locale.ROOT);
        if (slot == EntityEquipmentSlot.HEAD && name.equals("head")) {
            return bipedHead;
        }
        if (slot == EntityEquipmentSlot.CHEST) {
            if (name.equals("body")) return bipedBody;
            if (name.equals("rightarm")) return bipedRightArm;
            if (name.equals("leftarm")) return bipedLeftArm;
        }
        if (slot == EntityEquipmentSlot.LEGS) {
            if (name.equals("body")) return bipedBody;
            if (name.equals("rightleg")) return bipedRightLeg;
            if (name.equals("leftleg")) return bipedLeftLeg;
        }
        if (slot == EntityEquipmentSlot.FEET) {
            if (name.equals("rightleg")) return bipedRightLeg;
            if (name.equals("leftleg")) return bipedLeftLeg;
        }
        return null;
    }

    private void clearVanillaGeometry() {
        clear(bipedHead);
        clear(bipedHeadwear);
        clear(bipedBody);
        clear(bipedRightArm);
        clear(bipedLeftArm);
        clear(bipedRightLeg);
        clear(bipedLeftLeg);
    }

    private static void clear(ModelRenderer renderer) {
        renderer.cubeList.clear();
        if (renderer.childModels != null) {
            renderer.childModels.clear();
        }
    }

    private static String string(JsonObject object, String name) {
        JsonElement value = object.get(name);
        return value == null ? "" : value.getAsString();
    }

    private static float value(float[] values, int index) {
        return values != null && index < values.length ? values[index] : 0.0F;
    }

    private static float[] vector(float[] values) {
        return values == null || values.length < 3 ? new float[]{0.0F, 0.0F, 0.0F} : values;
    }

    private static int floor(float value) {
        return (int) Math.floor(value);
    }

    private static HeadMotionType headMotionType(String name, float height, float highestY) {
        if (name.equals("beard back")) {
            return null;
        }
        if (name.contains("beard")) {
            return HeadMotionType.BEARD;
        }
        if (name.contains("hairback") || name.contains("ponytail")
                || (name.contains("hair") && (height >= 12.0F || highestY <= 26.0F))) {
            return HeadMotionType.LONG_HAIR;
        }
        return null;
    }

    static float adjustedInflate(String name, float authoredInflate,
                                 float width, float depth) {
        return adjustedInflate(null, name, authoredInflate, width, depth);
    }

    static float adjustedInflate(OtsutsukiArmorSet armorSet, String name,
                                 float authoredInflate, float width, float depth) {
        if (armorSet == OtsutsukiArmorSet.KAGUYA) {
            if (name.contains("arm armor")) {
                return Math.min(authoredInflate, 0.42F);
            }
            if (name.contains("leg armor")) {
                return Math.min(authoredInflate, 0.35F);
            }
            if (name.contains("chestplate")) {
                return Math.min(authoredInflate, 0.16F);
            }
        }
        if (armorSet == OtsutsukiArmorSet.KINSHIKI
                && name.contains("arm armor lower")) {
            return Math.min(authoredInflate, 0.56F);
        }
        if (armorSet == OtsutsukiArmorSet.HAGOROMO && name.startsWith("dress ")) {
            return Math.min(authoredInflate, 0.38F);
        }
        if (name.contains("arm armor")) {
            if (name.contains("lower") || name.contains("under")) {
                return Math.min(authoredInflate, name.contains("under") ? 0.38F : 0.48F);
            }
            return Math.min(authoredInflate, width >= 5.0F ? 0.65F : 0.82F);
        }
        if (name.contains("leg armor")) {
            return Math.min(authoredInflate, width >= 5.0F || depth >= 5.0F ? 0.55F : 0.5F);
        }
        if (name.contains("boot")) {
            return Math.min(authoredInflate, 0.7F);
        }
        if (name.contains("chestplate")) {
            if (name.contains("outer")) {
                return Math.min(authoredInflate, 0.98F);
            }
            return Math.min(authoredInflate, name.contains("inside") ? 0.72F : 0.82F);
        }
        if (name.contains("belt")) {
            return Math.min(authoredInflate, name.contains("outer") ? 0.92F : 0.52F);
        }
        if (name.equals("hat layer")) {
            return Math.min(authoredInflate, 1.2F);
        }
        if (name.equals("helmet")) {
            return Math.min(authoredInflate, 0.85F);
        }
        if (name.equals("hair") && depth > 0.0F) {
            return Math.min(authoredInflate, 0.5F);
        }
        return authoredInflate;
    }

    static float constrainedLongHairPitch(float headPitch) {
        return headPitch <= 0.0F ? 0.0F
                : clamp(headPitch * 0.55F, 0.0F,
                HeadMotionPart.LONG_HAIR_PITCH_LIMIT);
    }

    static float constrainedBeardPitch(float headPitch) {
        return headPitch >= 0.0F
                ? clamp(headPitch * 0.35F, 0.0F, HeadMotionPart.BEARD_DOWN_PITCH_LIMIT)
                : clamp(headPitch * 0.80F, -HeadMotionPart.BEARD_PITCH_LIMIT, 0.0F);
    }

    static float pivotX(float origin, float parentOrigin) {
        return parentOrigin - origin;
    }

    static float pivotY(float origin, float parentOrigin) {
        return parentOrigin - origin;
    }

    static float pivotZ(float origin, float parentOrigin) {
        return origin - parentOrigin;
    }

    static float cubeX(float origin, float to) {
        return origin - to;
    }

    static float cubeY(float origin, float to) {
        return origin - to;
    }

    static float cubeZ(float origin, float from) {
        return from - origin;
    }

    static float centerPivot(float minimum, float size) {
        return minimum + size * 0.5F;
    }

    static float centeredMinimum(float size) {
        return size * -0.5F;
    }

    static float nearestAttachmentEdge(float first, float second) {
        return Math.abs(first) <= Math.abs(second) ? first : second;
    }

    static float robeMotionWeight(float amount) {
        float normalized = clamp(amount / 0.55F, 0.0F, 1.0F);
        return normalized * normalized * (3.0F - 2.0F * normalized);
    }

    static float robeProjectedLegShift(float pitch, float legReach) {
        float constrainedPitch = clamp(pitch, -1.20F, 1.20F);
        return clamp(MathHelper.sin(constrainedPitch) * legReach * ROBE_STRIDE_SCALE,
                -ROBE_STRIDE_LIMIT, ROBE_STRIDE_LIMIT);
    }

    static float robeBootSwing(float sourceLegPitch) {
        return clamp(sourceLegPitch * 0.08F, -0.11F, 0.11F);
    }

    static float robeBootTargetY(float sourceLegPitch, float bootHalfHeight) {
        float legReach = LONG_ROBE_HEM_Y - CircularRobeRenderer.LEG_PIVOT_Y;
        float hemLift = robeProjectedLegLift(sourceLegPitch, legReach)
                * ROBE_HEM_LIFT_SCALE;
        return LONG_ROBE_HEM_Y - bootHalfHeight - hemLift;
    }

    static float robeBootTargetZ(float sourceLegPitch, float oppositeLegPitch) {
        float legReach = LONG_ROBE_HEM_Y - CircularRobeRenderer.LEG_PIVOT_Y;
        float ownShift = robeProjectedLegShift(sourceLegPitch, legReach);
        float oppositeShift = robeProjectedLegShift(oppositeLegPitch, legReach);
        float robeCenter = (ownShift + oppositeShift) * 0.5F;
        return lerp(robeCenter, ownShift, COVERED_BOOT_SIDE_TRAVEL);
    }

    static float robeProjectedLegLift(float pitch, float legReach) {
        float constrainedPitch = clamp(pitch, -1.20F, 1.20F);
        return (1.0F - MathHelper.cos(constrainedPitch)) * legReach;
    }

    static float coveredBootCenterY(float height, float inflate) {
        float visibleTop = height * COVERED_BOOT_TOP_FRACTION;
        float visibleBottom = height + inflate;
        return (visibleTop + visibleBottom) * 0.5F;
    }

    static float coveredBootHalfHeight(float height, float inflate) {
        float visibleTop = height * COVERED_BOOT_TOP_FRACTION;
        float visibleBottom = height + inflate;
        return (visibleBottom - visibleTop) * 0.5F;
    }

    static float coveredBootInflate(float authoredInflate) {
        return Math.min(authoredInflate, COVERED_BOOT_MAX_INFLATE);
    }

    static float isshikiCollarCenterY(float y, float height) {
        return y - height * 0.08F;
    }

    static float isshikiCollarFrontZ(float z) {
        return z;
    }

    static float isshikiCollarSideX(float width) {
        return width * ISSHIKI_COLLAR_SIDE_FACTOR;
    }

    static float isshikiCollarBackBaseZ(float z, float depth) {
        return z + depth * ISSHIKI_COLLAR_BACK_BASE_FACTOR;
    }

    static float isshikiCollarBackDepth(float depth) {
        return depth * ISSHIKI_COLLAR_BACK_DEPTH_FACTOR;
    }

    static float isshikiCollarFrontDrop() {
        return ISSHIKI_COLLAR_FRONT_DROP;
    }

    static float isshikiCollarFrontDropProgress(float frontAmount) {
        float amount = clamp((frontAmount - ISSHIKI_COLLAR_FRONT_DROP_START)
                / (1.0F - ISSHIKI_COLLAR_FRONT_DROP_START), 0.0F, 1.0F);
        return amount * amount * (3.0F - 2.0F * amount);
    }

    static float isshikiCollarOuterRadius() {
        return ISSHIKI_COLLAR_OUTER_RADIUS;
    }

    static float isshikiCollarInnerRadius() {
        return ISSHIKI_COLLAR_INNER_RADIUS;
    }

    static float robeSilhouetteComponent(float circularComponent, float amount) {
        float circle = clamp(Math.abs(circularComponent), 0.0F, 1.0F);
        float transition = amount * amount * (3.0F - 2.0F * amount);
        float squareRoot = MathHelper.sqrt(circle);
        float roundedWaist = lerp(squareRoot, MathHelper.sqrt(squareRoot), 0.42F);
        float shaped = lerp(roundedWaist, circle, transition);
        return circularComponent < 0.0F ? -shaped : shaped;
    }

    static float robeSilhouetteComponent(float circularComponent, float amount,
                                         float waistExponent) {
        float circle = clamp(Math.abs(circularComponent), 0.0F, 1.0F);
        float transition = amount * amount * (3.0F - 2.0F * amount);
        float exponent = lerp(waistExponent, 1.0F, transition);
        float shaped = (float) Math.pow(circle, exponent);
        return circularComponent < 0.0F ? -shaped : shaped;
    }

    static float clockwiseAngle(float x, float y, float centerX, float centerY) {
        return (float) Math.atan2(y - centerY, x - centerX);
    }

    static boolean kinshikiHaloPixel(int column, int row) {
        return SmoothHaloBox.solid(column, row);
    }

    static boolean kinshikiRoundedHaloPixel(int column, int row) {
        return SmoothHaloBox.roundedSolid(column, row, 0.5F);
    }

    static int kinshikiHaloContourScale() {
        return SmoothHaloBox.CONTOUR_SCALE;
    }

    static float kinshikiHaloSurfaceDepth(float sourceX, float sourceY) {
        return SmoothHaloBox.surfaceDepth(sourceX, sourceY, 0.0F);
    }

    static float rotationX(float degrees) {
        return -degrees * DEGREES_TO_RADIANS;
    }

    static float rotationY(float degrees) {
        return -degrees * DEGREES_TO_RADIANS;
    }

    static float rotationZ(float degrees) {
        return degrees * DEGREES_TO_RADIANS;
    }

    private static final class ProjectData {
        private ResolutionData resolution;
        private List<ElementData> elements;
        private List<GroupData> groups;
        private JsonArray outliner;
    }

    private static final class ResolutionData {
        private int width;
        private int height;
    }

    private static final class GroupData {
        private String name;
        private String uuid;
        private float[] origin;
        private float[] rotation;
    }

    private static final class ElementData {
        private String name;
        private String uuid;
        private float[] from;
        private float[] to;
        private float[] origin;
        private float[] rotation;
        private float[] uvOffset;
        private float inflate;
        private boolean mirror;
        private Map<String, FaceData> faces;
    }

    private static final class FaceData {
        private float[] uv;
    }

    private enum HeadMotionType {
        LONG_HAIR,
        BEARD
    }

    private static final class HeadMotionPart {
        private static final float LONG_HAIR_PITCH_LIMIT = 18.0F * DEGREES_TO_RADIANS;
        private static final float BEARD_PITCH_LIMIT = 30.0F * DEGREES_TO_RADIANS;
        private static final float BEARD_DOWN_PITCH_LIMIT = 12.0F * DEGREES_TO_RADIANS;

        private final ModelRenderer renderer;
        private final boolean inheritsHeadRotation;
        private final HeadMotionType type;
        private final float baseRotationX;
        private final float baseRotationY;

        private HeadMotionPart(ModelRenderer renderer, boolean inheritsHeadRotation,
                               HeadMotionType type) {
            this.renderer = renderer;
            this.inheritsHeadRotation = inheritsHeadRotation;
            this.type = type;
            this.baseRotationX = renderer.rotateAngleX;
            this.baseRotationY = renderer.rotateAngleY;
        }

        private void animate(float headPitch, float headYaw) {
            float targetPitch = type == HeadMotionType.BEARD
                    ? constrainedBeardPitch(headPitch) : constrainedLongHairPitch(headPitch);
            float targetYaw = headYaw;
            renderer.rotateAngleX = baseRotationX + targetPitch
                    - (inheritsHeadRotation ? headPitch : 0.0F);
            renderer.rotateAngleY = baseRotationY + targetYaw
                    - (inheritsHeadRotation ? headYaw : 0.0F);
        }
    }

    private static final class AnimatedPart {
        private static final float FLOAT_SPEED = 0.075F;
        private static final float HALO_FLOAT_DISTANCE = 0.34F;
        private static final float ORB_FLOAT_DISTANCE = 0.24F;

        private final ModelRenderer floatingRenderer;
        private final ModelRenderer spinningRenderer;
        private final float baseY;
        private final float baseRotationX;
        private final float baseRotationY;
        private final float tiltPhase;
        private final float spinSpeed;

        private AnimatedPart(ModelRenderer floatingRenderer, ModelRenderer spinningRenderer,
                             String id) {
            this.floatingRenderer = floatingRenderer;
            this.spinningRenderer = spinningRenderer;
            this.baseY = floatingRenderer.rotationPointY;
            this.baseRotationX = spinningRenderer == null ? 0.0F : spinningRenderer.rotateAngleX;
            this.baseRotationY = spinningRenderer == null ? 0.0F : spinningRenderer.rotateAngleY;
            int hash = id == null ? 0 : id.hashCode();
            this.tiltPhase = (hash & 0xFFFF) / 65535.0F * TWO_PI;
            this.spinSpeed = 0.022F + ((hash >>> 16) & 7) * 0.0015F;
        }

        private void animate(float ageInTicks) {
            float distance = spinningRenderer == null
                    ? HALO_FLOAT_DISTANCE : ORB_FLOAT_DISTANCE;
            floatingRenderer.rotationPointY = baseY
                    + MathHelper.sin(ageInTicks * FLOAT_SPEED) * distance;
            if (spinningRenderer != null) {
                spinningRenderer.rotateAngleY = baseRotationY + ageInTicks * spinSpeed;
                spinningRenderer.rotateAngleX = baseRotationX
                        + MathHelper.sin(ageInTicks * 0.018F + tiltPhase) * 0.07F;
            }
        }
    }

    private static final class OrbitalPart {
        private static final float PATH_SPEED = 0.010F;

        private final ModelRenderer parentRenderer;
        private final ModelRenderer orbRenderer;
        private final float baseAbsoluteX;
        private final float baseAbsoluteY;
        private final float baseAbsoluteZ;
        private final float baseRotationX;
        private final float baseRotationY;
        private final float tiltPhase;
        private final float spinSpeed;
        private List<OrbPathPoint> path = Collections.emptyList();
        private int startIndex;

        private OrbitalPart(ModelRenderer parentRenderer, ModelRenderer orbRenderer, String id) {
            this.parentRenderer = parentRenderer;
            this.orbRenderer = orbRenderer;
            this.baseAbsoluteX = parentRenderer.rotationPointX + orbRenderer.rotationPointX;
            this.baseAbsoluteY = parentRenderer.rotationPointY + orbRenderer.rotationPointY;
            this.baseAbsoluteZ = parentRenderer.rotationPointZ + orbRenderer.rotationPointZ;
            this.baseRotationX = orbRenderer.rotateAngleX;
            this.baseRotationY = orbRenderer.rotateAngleY;
            int hash = id == null ? 0 : id.hashCode();
            this.tiltPhase = (hash & 0xFFFF) / 65535.0F * TWO_PI;
            this.spinSpeed = 0.022F + ((hash >>> 16) & 7) * 0.0015F;
        }

        private OrbPathPoint basePoint() {
            return new OrbPathPoint(baseAbsoluteX, baseAbsoluteY, baseAbsoluteZ);
        }

        private void setPath(List<OrbPathPoint> path, int startIndex) {
            this.path = path;
            this.startIndex = startIndex;
        }

        private void animate(float ageInTicks) {
            OrbPathPoint point = pathPoint(ageInTicks);
            float bob = MathHelper.sin(ageInTicks * AnimatedPart.FLOAT_SPEED + tiltPhase)
                    * 0.12F;
            orbRenderer.rotationPointX = point.x - parentRenderer.rotationPointX;
            orbRenderer.rotationPointY = point.y + bob - parentRenderer.rotationPointY;
            orbRenderer.rotationPointZ = point.z - parentRenderer.rotationPointZ;
            orbRenderer.rotateAngleY = baseRotationY + ageInTicks * spinSpeed;
            orbRenderer.rotateAngleX = baseRotationX
                    + MathHelper.sin(ageInTicks * 0.018F + tiltPhase) * 0.07F;
        }

        private OrbPathPoint pathPoint(float ageInTicks) {
            if (path.size() <= 1) {
                return basePoint();
            }
            float progress = ageInTicks * PATH_SPEED;
            int wholeSteps = floor(progress);
            float amount = progress - wholeSteps;
            int current = wrap(startIndex + wholeSteps, path.size());
            int next = wrap(current + 1, path.size());
            if (path.size() == 2) {
                float eased = amount * amount * (3.0F - 2.0F * amount);
                return OrbPathPoint.lerp(path.get(current), path.get(next), eased);
            }
            OrbPathPoint previous = path.get(wrap(current - 1, path.size()));
            OrbPathPoint following = path.get(wrap(next + 1, path.size()));
            return OrbPathPoint.catmull(previous, path.get(current),
                    path.get(next), following, amount);
        }

        private static int wrap(int value, int size) {
            int wrapped = value % size;
            return wrapped < 0 ? wrapped + size : wrapped;
        }
    }

    private static final class OrbPathPoint {
        private final float x;
        private final float y;
        private final float z;

        private OrbPathPoint(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        private static OrbPathPoint lerp(OrbPathPoint first, OrbPathPoint second,
                                         float amount) {
            return new OrbPathPoint(OtsutsukiArmorModel.lerp(first.x, second.x, amount),
                    OtsutsukiArmorModel.lerp(first.y, second.y, amount),
                    OtsutsukiArmorModel.lerp(first.z, second.z, amount));
        }

        private static OrbPathPoint catmull(OrbPathPoint previous, OrbPathPoint current,
                                            OrbPathPoint next, OrbPathPoint following,
                                            float amount) {
            return new OrbPathPoint(catmullValue(previous.x, current.x, next.x, following.x,
                    amount), catmullValue(previous.y, current.y, next.y, following.y, amount),
                    catmullValue(previous.z, current.z, next.z, following.z, amount));
        }

        private static float catmullValue(float previous, float current, float next,
                                          float following, float amount) {
            float squared = amount * amount;
            float cubed = squared * amount;
            return 0.5F * ((2.0F * current) + (-previous + next) * amount
                    + (2.0F * previous - 5.0F * current + 4.0F * next - following)
                    * squared
                    + (-previous + 3.0F * current - 3.0F * next + following) * cubed);
        }
    }

    private static final class TexturedPrismBox extends ModelBox {
        private final TexturedQuad[] quads;

        private TexturedPrismBox(ModelRenderer renderer, ElementData element,
                                 float topY, float bottomY,
                                 float topMinX, float topMaxX,
                                 float topMinZ, float topMaxZ,
                                 float bottomMinX, float bottomMaxX,
                                 float bottomMinZ, float bottomMaxZ) {
            this(renderer, element, topY, bottomY,
                    topMinX, topMaxX, topMinZ, topMaxZ,
                    bottomMinX, bottomMaxX, bottomMinZ, bottomMaxZ,
                    0.0F, 1.0F);
        }

        private TexturedPrismBox(ModelRenderer renderer, ElementData element,
                                 float topY, float bottomY,
                                 float topMinX, float topMaxX,
                                 float topMinZ, float topMaxZ,
                                 float bottomMinX, float bottomMaxX,
                                 float bottomMinZ, float bottomMaxZ,
                                 float sideVStart, float sideVEnd) {
            super(renderer, 0, 0, 0.0F, 0.0F, 0.0F, 0, 0, 0, 0.0F);
            PositionTextureVertex topNorthWest = vertex(topMinX, topY, topMinZ);
            PositionTextureVertex topNorthEast = vertex(topMaxX, topY, topMinZ);
            PositionTextureVertex topSouthWest = vertex(topMinX, topY, topMaxZ);
            PositionTextureVertex topSouthEast = vertex(topMaxX, topY, topMaxZ);
            PositionTextureVertex bottomNorthWest = vertex(bottomMinX, bottomY, bottomMinZ);
            PositionTextureVertex bottomNorthEast = vertex(bottomMaxX, bottomY, bottomMinZ);
            PositionTextureVertex bottomSouthWest = vertex(bottomMinX, bottomY, bottomMaxZ);
            PositionTextureVertex bottomSouthEast = vertex(bottomMaxX, bottomY, bottomMaxZ);
            quads = new TexturedQuad[]{
                    quad(renderer, element, "east", topSouthEast, topNorthEast,
                            bottomNorthEast, bottomSouthEast, sideVStart, sideVEnd),
                    quad(renderer, element, "west", topNorthWest, topSouthWest,
                            bottomSouthWest, bottomNorthWest, sideVStart, sideVEnd),
                    quad(renderer, element, "up", topSouthEast, topSouthWest,
                            topNorthWest, topNorthEast),
                    quad(renderer, element, "down", bottomNorthEast, bottomNorthWest,
                            bottomSouthWest, bottomSouthEast),
                    quad(renderer, element, "north", topNorthEast, topNorthWest,
                            bottomNorthWest, bottomNorthEast, sideVStart, sideVEnd),
                    quad(renderer, element, "south", topSouthWest, topSouthEast,
                            bottomSouthEast, bottomSouthWest, sideVStart, sideVEnd)
            };
        }

        @Override
        public void render(BufferBuilder renderer, float scale) {
            for (TexturedQuad quad : quads) {
                if (quad != null) {
                    quad.draw(renderer, scale);
                }
            }
        }

        private static PositionTextureVertex vertex(float x, float y, float z) {
            return new PositionTextureVertex(x, y, z, 0.0F, 0.0F);
        }

        private static TexturedQuad quad(ModelRenderer renderer, ElementData element,
                                         String faceName, PositionTextureVertex first,
                                         PositionTextureVertex second,
                                         PositionTextureVertex third,
                                         PositionTextureVertex fourth) {
            return quad(renderer, element, faceName, first, second, third, fourth,
                    0.0F, 1.0F);
        }

        private static TexturedQuad quad(ModelRenderer renderer, ElementData element,
                                         String faceName, PositionTextureVertex first,
                                         PositionTextureVertex second,
                                         PositionTextureVertex third,
                                         PositionTextureVertex fourth,
                                         float verticalStart, float verticalEnd) {
            FaceData face = element.faces == null ? null : element.faces.get(faceName);
            if (face == null || face.uv == null || face.uv.length < 4) {
                return null;
            }
            float firstV = lerp(face.uv[1], face.uv[3], verticalStart);
            float secondV = lerp(face.uv[1], face.uv[3], verticalEnd);
            return new TexturedQuad(new PositionTextureVertex[]{first, second, third, fourth},
                    floor(face.uv[0]), floor(firstV), floor(face.uv[2]), floor(secondV),
                    renderer.textureWidth, renderer.textureHeight);
        }
    }

    private abstract static class TexturedTriangleBox extends ModelBox {
        private final List<TexturedTriangle> triangles = new ArrayList<>();
        private final float atlasWidth;
        private final float atlasHeight;

        private TexturedTriangleBox(ModelRenderer renderer) {
            super(renderer, 0, 0, 0.0F, 0.0F, 0.0F, 0, 0, 0, 0.0F);
            atlasWidth = renderer.textureWidth;
            atlasHeight = renderer.textureHeight;
        }

        final void addQuad(MeshVertex first, MeshVertex second,
                           MeshVertex third, MeshVertex fourth) {
            addTriangle(first, second, third);
            addTriangle(first, third, fourth);
        }

        final void addTriangle(MeshVertex first, MeshVertex second, MeshVertex third) {
            Point faceNormal = normal(first.point, second.point, third.point);
            triangles.add(new TexturedTriangle(first, second, third, faceNormal));
        }

        @Override
        public void render(BufferBuilder renderer, float scale) {
            if (triangles.isEmpty()) {
                return;
            }
            GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT
                    | GL11.GL_POLYGON_BIT);
            try {
                GL11.glDisable(GL11.GL_CULL_FACE);
                GL11.glEnable(GL11.GL_TEXTURE_2D);
                GL11.glEnable(GL11.GL_ALPHA_TEST);
                GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                renderer.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_TEX_NORMAL);
                for (TexturedTriangle triangle : triangles) {
                    emit(renderer, triangle.first, triangle.normal, scale);
                    emit(renderer, triangle.second, triangle.normal, scale);
                    emit(renderer, triangle.third, triangle.normal, scale);
                }
                Tessellator.getInstance().draw();
            } finally {
                GL11.glPopAttrib();
            }
        }

        private void emit(BufferBuilder renderer, MeshVertex vertex,
                          Point normal, float scale) {
            renderer.pos(vertex.point.x * scale, vertex.point.y * scale,
                            vertex.point.z * scale)
                    .tex(vertex.u / atlasWidth, vertex.v / atlasHeight)
                    .normal(normal.x, normal.y, normal.z)
                    .endVertex();
        }

        static MeshVertex vertex(float x, float y, float z, float u, float v) {
            return new MeshVertex(new Point(x, y, z), u, v);
        }

        private static Point normal(Point first, Point second, Point third) {
            float firstX = second.x - first.x;
            float firstY = second.y - first.y;
            float firstZ = second.z - first.z;
            float secondX = third.x - first.x;
            float secondY = third.y - first.y;
            float secondZ = third.z - first.z;
            return normalize(new Point(
                    firstY * secondZ - firstZ * secondY,
                    firstZ * secondX - firstX * secondZ,
                    firstX * secondY - firstY * secondX));
        }
    }

    private static final class RobeBodiceBox extends TexturedTriangleBox {
        private static final int SEGMENTS = 32;
        private static final int ROWS = 5;

        private RobeBodiceBox(ModelRenderer renderer, ElementData element,
                              float topY, float bottomY,
                              float centerX, float centerZ,
                              float topRadiusX, float topRadiusZ,
                              float bottomRadiusX, float bottomRadiusZ) {
            super(renderer);
            FaceData[] sides = faces(face(element, "north"), face(element, "east"),
                    face(element, "south"), face(element, "west"));
            MeshVertex[][] vertices = new MeshVertex[ROWS + 1][SEGMENTS + 1];
            for (int row = 0; row <= ROWS; row++) {
                float vertical = row / (float) ROWS;
                float eased = vertical * vertical * (3.0F - 2.0F * vertical);
                float radiusX = lerp(topRadiusX, bottomRadiusX, eased);
                float radiusZ = lerp(topRadiusZ, bottomRadiusZ, eased);
                float y = lerp(topY, bottomY, vertical);
                for (int segment = 0; segment <= SEGMENTS; segment++) {
                    float angle = TWO_PI * segment / SEGMENTS;
                    int side = bodiceFaceIndex(angle);
                    FaceData texture = sides[side];
                    float u = bodiceFaceU(texture, angle, side);
                    float v = lerp(texture.uv[1], texture.uv[3], vertical);
                    vertices[row][segment] = vertex(
                            centerX + robeSilhouetteComponent(MathHelper.sin(angle), 0.0F)
                                    * radiusX,
                            y,
                            centerZ - robeSilhouetteComponent(MathHelper.cos(angle), 0.0F)
                                    * radiusZ,
                            u, v);
                }
            }
            for (int row = 0; row < ROWS; row++) {
                for (int segment = 0; segment < SEGMENTS; segment++) {
                    addQuad(vertices[row][segment], vertices[row + 1][segment],
                            vertices[row + 1][segment + 1], vertices[row][segment + 1]);
                }
            }
        }

        private static int bodiceFaceIndex(float angle) {
            return floor((angle + (float) Math.PI * 0.25F)
                    / ((float) Math.PI * 0.5F)) & 3;
        }

        private static float bodiceFaceU(FaceData face, float angle, int faceIndex) {
            float center = faceIndex * (float) Math.PI * 0.5F;
            float delta = angle - center;
            while (delta > Math.PI) delta -= TWO_PI;
            while (delta < -Math.PI) delta += TWO_PI;
            float amount = clamp(delta / ((float) Math.PI * 0.5F) + 0.5F,
                    0.0F, 1.0F);
            return lerp(face.uv[0], face.uv[2], amount);
        }
    }

    private static final class ToneriCollarPanelBox extends TexturedTriangleBox {
        private static final int ROWS = 4;
        private static final int COLUMNS = 12;
        private static final float EDGE_HALF_THICKNESS = 0.14F;

        private ToneriCollarPanelBox(ModelRenderer renderer, ElementData element,
                                     float x, float y, float z,
                                     float width, float height) {
            super(renderer);
            FaceData visibleFace = face(element, "south");
            MeshVertex[][] front = surface(visibleFace, x, y, z,
                    width, height, -1.0F);
            MeshVertex[][] back = surface(visibleFace, x, y, z,
                    width, height, 1.0F);
            for (int row = 0; row < ROWS; row++) {
                for (int column = 0; column < COLUMNS; column++) {
                    addQuad(front[row][column], front[row + 1][column],
                            front[row + 1][column + 1], front[row][column + 1]);
                    addQuad(back[row][column + 1], back[row + 1][column + 1],
                            back[row + 1][column], back[row][column]);
                }
            }
            for (int column = 0; column < COLUMNS; column++) {
                addQuad(back[0][column], front[0][column],
                        front[0][column + 1], back[0][column + 1]);
                addQuad(front[ROWS][column], back[ROWS][column],
                        back[ROWS][column + 1], front[ROWS][column + 1]);
            }
            for (int row = 0; row < ROWS; row++) {
                addQuad(back[row][0], back[row + 1][0],
                        front[row + 1][0], front[row][0]);
                addQuad(front[row][COLUMNS], front[row + 1][COLUMNS],
                        back[row + 1][COLUMNS], back[row][COLUMNS]);
            }
        }

        private static MeshVertex[][] surface(FaceData face,
                                               float x, float y, float z,
                                               float width, float height,
                                               float side) {
            MeshVertex[][] vertices = new MeshVertex[ROWS + 1][COLUMNS + 1];
            for (int row = 0; row <= ROWS; row++) {
                float vertical = row / (float) ROWS;
                for (int column = 0; column <= COLUMNS; column++) {
                    float across = column / (float) COLUMNS;
                    float taper = 0.88F + vertical * 0.12F;
                    float gentleBow = MathHelper.sin((float) Math.PI * across) * 0.20F;
                    float paddedRoll = MathHelper.sin((float) Math.PI * vertical) * 0.34F;
                    float thickness = EDGE_HALF_THICKNESS
                            + MathHelper.sin((float) Math.PI * vertical) * 0.12F
                            + MathHelper.sin((float) Math.PI * across) * 0.06F;
                    float u = lerp(face.uv[0], face.uv[2], across);
                    float v = lerp(face.uv[1], face.uv[3], vertical);
                    vertices[row][column] = vertex(
                            x + width * (0.5F + (across - 0.5F) * taper),
                            y + height * vertical,
                            z - gentleBow - paddedRoll + side * thickness, u, v);
                }
            }
            return vertices;
        }
    }

    private static final class ToneriSashPanelBox extends TexturedTriangleBox {
        private static final float EDGE_HALF_THICKNESS = 0.14F;
        private static final float CENTER_PADDING = 0.22F;
        private static final float[] A = {0.00F, 0.00F};
        private static final float[] B = {0.89F, 0.00F};
        private static final float[] C = {0.89F, 0.40F};
        private static final float[] D = {0.34F, 0.40F};
        private static final float[] E = {0.34F, 1.00F};
        private static final float[] F = {0.22F, 1.00F};
        private static final float[] G = {0.11F, 0.50F};
        private static final float[] H = {0.00F, 0.40F};

        private ToneriSashPanelBox(ModelRenderer renderer, ElementData element,
                                   float x, float y, float z,
                                   float height, float depth) {
            super(renderer);
            FaceData texture = face(element, "west");
            addCurvedPatch(texture, x, y, z, height, depth,
                    1.0F, A, B, C, H, 7, 4);
            addCurvedPatch(texture, x, y, z, height, depth,
                    1.0F, G, D, E, F, 3, 7);
            addCurvedPatch(texture, x, y, z, height, depth,
                    -1.0F, A, B, C, H, 7, 4);
            addCurvedPatch(texture, x, y, z, height, depth,
                    -1.0F, G, D, E, F, 3, 7);
            float[][] outline = {A, B, C, D, E, F, G, H};
            for (int index = 0; index < outline.length; index++) {
                addEdge(texture, x, y, z, height, depth,
                        outline[index], outline[(index + 1) % outline.length]);
            }
        }

        private void addCurvedPatch(FaceData face, float x, float y, float z,
                                    float height, float depth, float side,
                                    float[] topLeft, float[] topRight,
                                    float[] bottomRight, float[] bottomLeft,
                                    int columns, int rows) {
            MeshVertex[][] vertices = new MeshVertex[rows + 1][columns + 1];
            for (int row = 0; row <= rows; row++) {
                float vertical = row / (float) rows;
                float leftAcross = lerp(topLeft[0], bottomLeft[0], vertical);
                float leftVertical = lerp(topLeft[1], bottomLeft[1], vertical);
                float rightAcross = lerp(topRight[0], bottomRight[0], vertical);
                float rightVertical = lerp(topRight[1], bottomRight[1], vertical);
                for (int column = 0; column <= columns; column++) {
                    float across = column / (float) columns;
                    float[] location = {
                            lerp(leftAcross, rightAcross, across),
                            lerp(leftVertical, rightVertical, across)
                    };
                    float padding = MathHelper.sin((float) Math.PI * across)
                            * MathHelper.sin((float) Math.PI * vertical)
                            * CENTER_PADDING;
                    vertices[row][column] = point(face, x, y, z, height, depth,
                            side, location, padding);
                }
            }
            for (int row = 0; row < rows; row++) {
                for (int column = 0; column < columns; column++) {
                    if (side > 0.0F) {
                        addQuad(vertices[row][column], vertices[row][column + 1],
                                vertices[row + 1][column + 1], vertices[row + 1][column]);
                    } else {
                        addQuad(vertices[row][column + 1], vertices[row][column],
                                vertices[row + 1][column], vertices[row + 1][column + 1]);
                    }
                }
            }
        }

        private void addEdge(FaceData face, float x, float y, float z,
                             float height, float depth,
                             float[] first, float[] second) {
            addQuad(point(face, x, y, z, height, depth, 1.0F, first),
                    point(face, x, y, z, height, depth, 1.0F, second),
                    point(face, x, y, z, height, depth, -1.0F, second),
                    point(face, x, y, z, height, depth, -1.0F, first));
        }

        private static MeshVertex point(FaceData face, float x, float y, float z,
                                        float height, float depth, float side,
                                        float[] point) {
            return point(face, x, y, z, height, depth, side, point, 0.0F);
        }

        private static MeshVertex point(FaceData face, float x, float y, float z,
                                        float height, float depth, float side,
                                        float[] point, float padding) {
            float across = point[0];
            float vertical = point[1];
            float clothBow = MathHelper.sin((float) Math.PI * vertical) * 0.12F;
            return vertex(x + clothBow + side * (EDGE_HALF_THICKNESS + padding),
                    y + height * vertical, z + depth * across,
                    lerp(face.uv[0], face.uv[2], across),
                    lerp(face.uv[1], face.uv[3], vertical));
        }
    }

    /** Hides the boot shaft under a long robe while retaining the visible shoe. */
    private static final class AdaptiveBootBox extends ModelBox {
        private final OtsutsukiArmorModel model;
        private final ModelBox fullBoot;
        private final TexturedPrismBox coveredBoot;

        private AdaptiveBootBox(OtsutsukiArmorModel model, ModelRenderer renderer,
                                ElementData element, float x, float y, float z,
                                float width, float height, float depth, float inflate) {
            super(renderer, 0, 0, 0.0F, 0.0F, 0.0F, 0, 0, 0, 0.0F);
            this.model = model;
            this.fullBoot = new ModelBox(renderer,
                    floor(value(element.uvOffset, 0)), floor(value(element.uvOffset, 1)),
                    x, y, z, floor(width), floor(height), floor(depth),
                    inflate, element.mirror);
            float coveredInflate = coveredBootInflate(inflate);
            model.coveredBootCenterY = coveredBootCenterY(height, coveredInflate);
            model.coveredBootHalfHeight = coveredBootHalfHeight(height, coveredInflate);
            float coveredTop = y + height * COVERED_BOOT_TOP_FRACTION;
            this.coveredBoot = new TexturedPrismBox(renderer, element,
                    coveredTop, y + height + coveredInflate,
                    x - coveredInflate, x + width + coveredInflate,
                    z - coveredInflate, z + depth + coveredInflate,
                    x - coveredInflate, x + width + coveredInflate,
                    z - coveredInflate, z + depth + coveredInflate,
                    COVERED_BOOT_TOP_FRACTION, 1.0F);
        }

        @Override
        public void render(BufferBuilder renderer, float scale) {
            if (model.coveredByLongRobe) {
                coveredBoot.render(renderer, scale);
            } else {
                fullBoot.render(renderer, scale);
            }
        }
    }

    private static final class CircularRobeRenderer extends ModelRenderer {
        private static final int SEGMENTS = 32;
        private static final int VERTICAL_SEGMENTS = 12;
        private static final float HEM_THICKNESS = 0.24F;
        private static final float LEG_PIVOT_Y = 12.0F;

        private final float topY;
        private final float bottomY;
        private final float topRadiusX;
        private final float topRadiusZ;
        private final float bottomRadiusX;
        private final float bottomRadiusZ;
        private final float waistExponent;
        private final FaceData[] faces;
        private final float atlasWidth;
        private final float atlasHeight;
        private float rightLegPitch;
        private float leftLegPitch;
        private float movement;
        private float ageTicks;

        private CircularRobeRenderer(OtsutsukiArmorModel model,
                                     float topY, float bottomY,
                                     float topRadiusX, float topRadiusZ,
                                     float bottomRadiusX, float bottomRadiusZ,
                                     float waistExponent,
                                     FaceData[] faces) {
            super(model);
            this.topY = topY;
            this.bottomY = bottomY;
            this.topRadiusX = topRadiusX;
            this.topRadiusZ = topRadiusZ;
            this.bottomRadiusX = bottomRadiusX;
            this.bottomRadiusZ = bottomRadiusZ;
            this.waistExponent = waistExponent;
            this.faces = faces;
            this.atlasWidth = model.textureWidth;
            this.atlasHeight = model.textureHeight;
            setTextureSize((int) atlasWidth, (int) atlasHeight);
        }

        private void animate(float rightLegPitch, float leftLegPitch,
                             float movement, float ageTicks) {
            this.rightLegPitch = rightLegPitch;
            this.leftLegPitch = leftLegPitch;
            this.movement = clamp(movement, 0.0F, 1.0F);
            this.ageTicks = ageTicks;
        }

        @Override
        public void render(float scale) {
            if (isHidden || !showModel) {
                return;
            }
            GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT
                    | GL11.GL_POLYGON_BIT);
            try {
                GL11.glDisable(GL11.GL_CULL_FACE);
                GL11.glEnable(GL11.GL_TEXTURE_2D);
                GL11.glEnable(GL11.GL_ALPHA_TEST);
                GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                BufferBuilder buffer = Tessellator.getInstance().getBuffer();
                buffer.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_TEX_NORMAL);
                for (int row = 0; row < VERTICAL_SEGMENTS; row++) {
                    renderBand(buffer, scale,
                            row / (float) VERTICAL_SEGMENTS,
                            (row + 1) / (float) VERTICAL_SEGMENTS);
                }
                renderHem(buffer, scale);
                Tessellator.getInstance().draw();
            } finally {
                GL11.glPopAttrib();
            }
        }

        private void renderBand(BufferBuilder buffer, float scale,
                                float upperAmount, float lowerAmount) {
            for (int segment = 0; segment < SEGMENTS; segment++) {
                float firstAngle = TWO_PI * segment / SEGMENTS;
                float secondAngle = TWO_PI * (segment + 1) / SEGMENTS;
                float middleAngle = (firstAngle + secondAngle) * 0.5F;
                int faceIndex = faceIndex(middleAngle);
                FaceData face = faces[faceIndex];
                float firstU = faceU(face, firstAngle, faceIndex);
                float secondU = faceU(face, secondAngle, faceIndex);
                float upperV = lerp(face.uv[1], face.uv[3], upperAmount);
                float lowerV = lerp(face.uv[1], face.uv[3], lowerAmount);
                RobePoint upperFirst = point(firstAngle, upperAmount, 0.0F, 0.0F);
                RobePoint upperSecond = point(secondAngle, upperAmount, 0.0F, 0.0F);
                RobePoint lowerFirst = point(firstAngle, lowerAmount, 0.0F, 0.0F);
                RobePoint lowerSecond = point(secondAngle, lowerAmount, 0.0F, 0.0F);
                texturedQuad(buffer, scale,
                        upperFirst, firstU, upperV,
                        lowerFirst, firstU, lowerV,
                        lowerSecond, secondU, lowerV,
                        upperSecond, secondU, upperV);
            }
        }

        private void renderHem(BufferBuilder buffer, float scale) {
            for (int segment = 0; segment < SEGMENTS; segment++) {
                float firstAngle = TWO_PI * segment / SEGMENTS;
                float secondAngle = TWO_PI * (segment + 1) / SEGMENTS;
                float middleAngle = (firstAngle + secondAngle) * 0.5F;
                int faceIndex = faceIndex(middleAngle);
                FaceData face = faces[faceIndex];
                float firstU = faceU(face, firstAngle, faceIndex);
                float secondU = faceU(face, secondAngle, faceIndex);
                float outerV = face.uv[3];
                float innerV = Math.max(face.uv[1], outerV - 0.45F);
                RobePoint outerFirst = point(firstAngle, 1.0F, 0.0F, 0.0F);
                RobePoint outerSecond = point(secondAngle, 1.0F, 0.0F, 0.0F);
                RobePoint innerFirst = point(firstAngle, 1.0F,
                        HEM_THICKNESS, -HEM_THICKNESS);
                RobePoint innerSecond = point(secondAngle, 1.0F,
                        HEM_THICKNESS, -HEM_THICKNESS);
                texturedQuad(buffer, scale,
                        outerFirst, firstU, outerV,
                        outerSecond, secondU, outerV,
                        innerSecond, secondU, innerV,
                        innerFirst, firstU, innerV);
            }
        }

        private RobePoint point(float angle, float amount,
                                float radialInset, float verticalOffset) {
            float eased = amount * amount * (3.0F - 2.0F * amount);
            float currentY = lerp(topY, bottomY, amount);
            float legReach = Math.max(0.0F, currentY - LEG_PIVOT_Y);
            float motionWeight = robeMotionWeight(amount);
            float rightShift = robeProjectedLegShift(rightLegPitch, legReach) * motionWeight;
            float leftShift = robeProjectedLegShift(leftLegPitch, legReach) * motionWeight;
            float centerShift = (rightShift + leftShift) * 0.5F;
            float halfDifference = (leftShift - rightShift) * 0.5F;
            float separation = Math.abs(halfDifference);
            float strideExpansion = Math.max(Math.abs(rightShift), Math.abs(leftShift));
            float radiusX = lerp(topRadiusX, bottomRadiusX, eased)
                    + strideExpansion * 0.12F - radialInset;
            float radiusZ = lerp(topRadiusZ, bottomRadiusZ, eased)
                    + separation * 0.68F - radialInset;
            float sine = MathHelper.sin(angle);
            float cosine = MathHelper.cos(angle);
            float silhouetteX = waistExponent > 0.0F
                    ? robeSilhouetteComponent(sine, amount, waistExponent)
                    : robeSilhouetteComponent(sine, amount);
            float silhouetteZ = waistExponent > 0.0F
                    ? robeSilhouetteComponent(cosine, amount, waistExponent)
                    : robeSilhouetteComponent(cosine, amount);
            float rightWeight = (1.0F - sine) * 0.5F;
            float rightLift = robeProjectedLegLift(rightLegPitch, legReach) * motionWeight;
            float leftLift = robeProjectedLegLift(leftLegPitch, legReach) * motionWeight;
            float adjacentLift = rightLift * rightWeight
                    + leftLift * (1.0F - rightWeight);
            float clothWeight = motionWeight;
            float idle = MathHelper.sin(ageTicks * 0.055F + angle * 1.7F)
                    * (0.025F + movement * 0.045F) * clothWeight;
            float lateral = MathHelper.sin(ageTicks * 0.08F + angle)
                    * movement * 0.055F * clothWeight;
            return new RobePoint(silhouetteX * radiusX + lateral,
                    currentY - adjacentLift * ROBE_HEM_LIFT_SCALE + verticalOffset,
                    -silhouetteZ * radiusZ + centerShift
                            + sine * halfDifference + idle);
        }

        private static int faceIndex(float angle) {
            int index = floor((angle + (float) Math.PI * 0.25F)
                    / ((float) Math.PI * 0.5F));
            return index & 3;
        }

        private static float faceU(FaceData face, float angle, int faceIndex) {
            float center = faceIndex * (float) Math.PI * 0.5F;
            float delta = angle - center;
            while (delta > Math.PI) {
                delta -= TWO_PI;
            }
            while (delta < -Math.PI) {
                delta += TWO_PI;
            }
            float amount = clamp(delta / ((float) Math.PI * 0.5F) + 0.5F,
                    0.0F, 1.0F);
            return lerp(face.uv[0], face.uv[2], amount);
        }

        private void texturedQuad(BufferBuilder buffer, float scale,
                                  RobePoint first, float firstU, float firstV,
                                  RobePoint second, float secondU, float secondV,
                                  RobePoint third, float thirdU, float thirdV,
                                  RobePoint fourth, float fourthU, float fourthV) {
            Point firstNormal = normal(first, second, third);
            emit(buffer, first, firstU, firstV, firstNormal, scale);
            emit(buffer, second, secondU, secondV, firstNormal, scale);
            emit(buffer, third, thirdU, thirdV, firstNormal, scale);
            Point secondNormal = normal(first, third, fourth);
            emit(buffer, first, firstU, firstV, secondNormal, scale);
            emit(buffer, third, thirdU, thirdV, secondNormal, scale);
            emit(buffer, fourth, fourthU, fourthV, secondNormal, scale);
        }

        private void emit(BufferBuilder buffer, RobePoint point,
                          float u, float v, Point normal, float scale) {
            buffer.pos(point.x * scale, point.y * scale, point.z * scale)
                    .tex(u / atlasWidth, v / atlasHeight)
                    .normal(normal.x, normal.y, normal.z)
                    .endVertex();
        }

        private static Point normal(RobePoint first, RobePoint second,
                                    RobePoint third) {
            float firstX = second.x - first.x;
            float firstY = second.y - first.y;
            float firstZ = second.z - first.z;
            float secondX = third.x - first.x;
            float secondY = third.y - first.y;
            float secondZ = third.z - first.z;
            return normalize(new Point(
                    firstY * secondZ - firstZ * secondY,
                    firstZ * secondX - firstX * secondZ,
                    firstX * secondY - firstY * secondX));
        }
    }

    private static final class RobePoint {
        private final float x;
        private final float y;
        private final float z;

        private RobePoint(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private enum MeshStyle {
        ORB_BASE,
        ORB_CORE_GLOW,
        ORB_RIM_GLOW,
        HALO_BASE,
        HALO_CORE_GLOW,
        HALO_RIM_GLOW
    }

    private abstract static class ColoredMeshBox extends ModelBox {
        private final List<Triangle> triangles = new ArrayList<>();

        ColoredMeshBox(ModelRenderer renderer) {
            super(renderer, 0, 0, 0.0F, 0.0F, 0.0F, 0, 0, 0, 0.0F);
        }

        @Override
        public void render(BufferBuilder renderer, float scale) {
            if (triangles.isEmpty()) {
                return;
            }
            GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT
                    | GL11.GL_LIGHTING_BIT | GL11.GL_POLYGON_BIT);
            try {
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                GL11.glDisable(GL11.GL_LIGHTING);
                GL11.glDisable(GL11.GL_CULL_FACE);
                GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                renderer.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
                for (Triangle triangle : triangles) {
                    emit(renderer, triangle.first, scale);
                    emit(renderer, triangle.second, scale);
                    emit(renderer, triangle.third, scale);
                }
                Tessellator.getInstance().draw();
            } finally {
                GL11.glPopAttrib();
            }
        }

        private static void emit(BufferBuilder renderer, ColoredVertex vertex, float scale) {
            renderer.pos(vertex.point.x * scale, vertex.point.y * scale, vertex.point.z * scale)
                    .color(vertex.color.red, vertex.color.green, vertex.color.blue,
                            vertex.color.alpha)
                    .endVertex();
        }

        void addTriangle(ColoredVertex first, ColoredVertex second, ColoredVertex third) {
            triangles.add(new Triangle(first, second, third));
        }
    }

    private static final class OpenRolledCollarBox extends ColoredMeshBox {
        private static final int SIDE_SEGMENTS = 16;
        private static final int BACK_SEGMENTS = 16;
        private static final int PATH_SEGMENTS = SIDE_SEGMENTS * 2 + BACK_SEGMENTS;
        private static final int RING_SEGMENTS = 12;

        private OpenRolledCollarBox(ModelRenderer renderer, ElementData element,
                                    float x, float y, float z,
                                    float width, float height, float depth) {
            super(renderer);
            float centerX = x + width * 0.5F;
            float centerY = isshikiCollarCenterY(y, height);
            float sideX = isshikiCollarSideX(width);
            float frontZ = isshikiCollarFrontZ(z);
            float backBaseZ = isshikiCollarBackBaseZ(z, depth);
            float backDepth = isshikiCollarBackDepth(depth);
            Point[] centers = new Point[PATH_SEGMENTS + 1];
            for (int path = 0; path <= PATH_SEGMENTS; path++) {
                centers[path] = collarPath(path, centerX, centerY,
                        sideX, frontZ, backBaseZ, backDepth);
            }

            ColoredVertex[][] rings =
                    new ColoredVertex[PATH_SEGMENTS + 1][RING_SEGMENTS + 1];
            for (int path = 0; path <= PATH_SEGMENTS; path++) {
                Point pathPoint = centers[path];
                Point outward = collarOutward(centers, path);
                for (int ring = 0; ring <= RING_SEGMENTS; ring++) {
                    float ringAngle = TWO_PI * ring / RING_SEGMENTS;
                    float planar = MathHelper.cos(ringAngle);
                    float vertical = MathHelper.sin(ringAngle);
                    float radialRadius = (planar >= 0.0F
                            ? ISSHIKI_COLLAR_OUTER_RADIUS
                            : ISSHIKI_COLLAR_INNER_RADIUS);
                    Point normal = normalize(new Point(
                            outward.x * planar, vertical, outward.z * planar));
                    Point point = new Point(
                            pathPoint.x + outward.x * radialRadius * planar,
                            pathPoint.y + ISSHIKI_COLLAR_VERTICAL_RADIUS * vertical,
                            pathPoint.z + outward.z * radialRadius * planar);
                    rings[path][ring] = new ColoredVertex(point, normal,
                            collarColor(normal));
                }
            }
            for (int path = 0; path < PATH_SEGMENTS; path++) {
                for (int ring = 0; ring < RING_SEGMENTS; ring++) {
                    addTriangle(rings[path][ring], rings[path + 1][ring],
                            rings[path + 1][ring + 1]);
                    addTriangle(rings[path][ring], rings[path + 1][ring + 1],
                            rings[path][ring + 1]);
                }
            }
            Point startDirection = normalize(subtract(centers[0], centers[1]));
            Point endDirection = normalize(subtract(
                    centers[PATH_SEGMENTS], centers[PATH_SEGMENTS - 1]));
            cap(rings[0], centers[0], startDirection, true);
            cap(rings[PATH_SEGMENTS], centers[PATH_SEGMENTS], endDirection, false);
        }

        private static Point collarPath(int path, float centerX, float centerY,
                                        float sideX, float frontZ,
                                        float backBaseZ, float backDepth) {
            if (path <= SIDE_SEGMENTS) {
                float amount = path / (float) SIDE_SEGMENTS;
                float frontDrop = isshikiCollarFrontDropProgress(1.0F - amount);
                return new Point(centerX + sideX,
                        centerY + frontDrop * ISSHIKI_COLLAR_FRONT_DROP,
                        lerp(frontZ, backBaseZ, amount));
            }
            if (path <= SIDE_SEGMENTS + BACK_SEGMENTS) {
                float amount = (path - SIDE_SEGMENTS) / (float) BACK_SEGMENTS;
                float angle = (float) Math.PI * amount;
                return new Point(centerX + MathHelper.cos(angle) * sideX,
                        centerY,
                        backBaseZ + MathHelper.sin(angle) * backDepth);
            }
            float amount = (path - SIDE_SEGMENTS - BACK_SEGMENTS)
                    / (float) SIDE_SEGMENTS;
            float frontDrop = isshikiCollarFrontDropProgress(amount);
            return new Point(centerX - sideX,
                    centerY + frontDrop * ISSHIKI_COLLAR_FRONT_DROP,
                    lerp(backBaseZ, frontZ, amount));
        }

        private static Point collarOutward(Point[] centers, int path) {
            Point previous = centers[Math.max(0, path - 1)];
            Point next = centers[Math.min(PATH_SEGMENTS, path + 1)];
            Point tangent = subtract(next, previous);
            return normalize(new Point(tangent.z, 0.0F, -tangent.x));
        }

        private void cap(ColoredVertex[] ring, Point center, Point normal,
                         boolean reverse) {
            ColoredVertex middle = new ColoredVertex(center, normal, collarColor(normal));
            for (int index = 0; index < RING_SEGMENTS; index++) {
                if (reverse) {
                    addTriangle(middle, ring[index + 1], ring[index]);
                } else {
                    addTriangle(middle, ring[index], ring[index + 1]);
                }
            }
        }

        private static ColorValue collarColor(Point normal) {
            float light = 0.78F + Math.max(0.0F, -normal.y) * 0.22F;
            return new ColorValue(0.596F * light, 0.212F * light,
                    0.290F * light, 1.0F);
        }
    }

    private static final class SmoothSphereBox extends ColoredMeshBox {
        private static final int LATITUDE_SEGMENTS = 12;
        private static final int LONGITUDE_SEGMENTS = 20;

        SmoothSphereBox(ModelRenderer renderer, float x, float y, float z,
                        float width, float height, float depth, float inflate,
                        MeshStyle style) {
            super(renderer);
            float centerX = x + width * 0.5F;
            float centerY = y + height * 0.5F;
            float centerZ = z + depth * 0.5F;
            float radiusX = width * 0.5F + inflate;
            float radiusY = height * 0.5F + inflate;
            float radiusZ = depth * 0.5F + inflate;
            for (int latitude = 0; latitude < LATITUDE_SEGMENTS; latitude++) {
                float latitude0 = -(float) Math.PI * 0.5F
                        + (float) Math.PI * latitude / LATITUDE_SEGMENTS;
                float latitude1 = -(float) Math.PI * 0.5F
                        + (float) Math.PI * (latitude + 1) / LATITUDE_SEGMENTS;
                for (int longitude = 0; longitude < LONGITUDE_SEGMENTS; longitude++) {
                    float longitude0 = TWO_PI * longitude / LONGITUDE_SEGMENTS;
                    float longitude1 = TWO_PI * (longitude + 1) / LONGITUDE_SEGMENTS;
                    ColoredVertex first = sphereVertex(centerX, centerY, centerZ,
                            radiusX, radiusY, radiusZ, latitude0, longitude0, style);
                    ColoredVertex second = sphereVertex(centerX, centerY, centerZ,
                            radiusX, radiusY, radiusZ, latitude0, longitude1, style);
                    ColoredVertex third = sphereVertex(centerX, centerY, centerZ,
                            radiusX, radiusY, radiusZ, latitude1, longitude1, style);
                    ColoredVertex fourth = sphereVertex(centerX, centerY, centerZ,
                            radiusX, radiusY, radiusZ, latitude1, longitude0, style);
                    addTriangle(first, second, third);
                    addTriangle(first, third, fourth);
                }
            }
        }

        private static ColoredVertex sphereVertex(float centerX, float centerY, float centerZ,
                                                   float radiusX, float radiusY, float radiusZ,
                                                   float latitude, float longitude,
                                                   MeshStyle style) {
            float latitudeRadius = MathHelper.cos(latitude);
            Point normal = normalize(new Point(latitudeRadius * MathHelper.cos(longitude),
                    MathHelper.sin(latitude), latitudeRadius * MathHelper.sin(longitude)));
            Point point = new Point(centerX + normal.x * radiusX,
                    centerY + normal.y * radiusY, centerZ + normal.z * radiusZ);
            return new ColoredVertex(point, normal, orbColor(style, normal));
        }
    }

    private static final class SmoothHaloBox extends ColoredMeshBox {
        private static final int CONTOUR_SCALE = 4;
        private static final int DEPTH_SEGMENTS = 16;
        private static final float DEPTH_LIMIT = 1.12F;
        private static final float PROFILE_RADIUS = 1.35F;
        private static final int[][] TETRAHEDRA = {
                {0, 5, 1, 6}, {0, 1, 2, 6}, {0, 2, 3, 6},
                {0, 3, 7, 6}, {0, 7, 4, 6}, {0, 4, 5, 6}
        };
        private static final String[] SHAPE = {
                ".....DDMMMMDD.....",
                "....DMLHHHHLMD....",
                "...DLHHHHHHHHLD...",
                "..DLHLMDDDDMLHLD..",
                "..MLLD......DLLM..",
                ".DLMD........DMLD.",
                ".MLD..........DLM.",
                "DLMD..........DMLD",
                "MLD............DLM",
                "LLD............DLL",
                "LMD............DML",
                "HM..............MH",
                "HD..............DH",
                "HD..............DH",
                "HD..............DH",
                "HD..............DH",
                "LD..............DL",
                "LD..............DL",
                "LM..............ML",
                "LH..............HL",
                "DH..............HD",
                ".LD.MLM....MLM.DL.",
                ".DMDLLD....DLLDMD.",
                "..DMLMD....DMLMD..",
                "....DDD....DDD...."
        };
        private static final float[][] PROFILE = createProfile();

        private final float modelX;
        private final float modelY;
        private final float modelZ;
        private final float modelWidth;
        private final float modelHeight;
        private final float halfDepth;
        private final float profileOffset;
        private final MeshStyle style;

        SmoothHaloBox(ModelRenderer renderer, float x, float y, float z,
                      float width, float height, float halfDepth,
                      float profileOffset, MeshStyle style) {
            super(renderer);
            this.modelX = x;
            this.modelY = y;
            this.modelZ = z;
            this.modelWidth = width;
            this.modelHeight = height;
            this.halfDepth = halfDepth;
            this.profileOffset = profileOffset;
            this.style = style;
            buildMesh();
        }

        private void buildMesh() {
            int columns = SHAPE[0].length() * CONTOUR_SCALE;
            int rows = SHAPE.length * CONTOUR_SCALE;
            FieldVertex[][][] grid = new FieldVertex[DEPTH_SEGMENTS + 1][rows + 1][columns + 1];
            for (int depthIndex = 0; depthIndex <= DEPTH_SEGMENTS; depthIndex++) {
                float depthNormal = lerp(-DEPTH_LIMIT, DEPTH_LIMIT,
                        depthIndex / (float) DEPTH_SEGMENTS);
                for (int row = 0; row <= rows; row++) {
                    float sourceY = row / (float) CONTOUR_SCALE;
                    for (int column = 0; column <= columns; column++) {
                        float sourceX = column / (float) CONTOUR_SCALE;
                        grid[depthIndex][row][column] = fieldVertex(sourceX, sourceY, depthNormal);
                    }
                }
            }
            for (int depthIndex = 0; depthIndex < DEPTH_SEGMENTS; depthIndex++) {
                for (int row = 0; row < rows; row++) {
                    for (int column = 0; column < columns; column++) {
                        FieldVertex[] cube = {
                                grid[depthIndex][row][column],
                                grid[depthIndex][row][column + 1],
                                grid[depthIndex][row + 1][column + 1],
                                grid[depthIndex][row + 1][column],
                                grid[depthIndex + 1][row][column],
                                grid[depthIndex + 1][row][column + 1],
                                grid[depthIndex + 1][row + 1][column + 1],
                                grid[depthIndex + 1][row + 1][column]
                        };
                        for (int[] tetrahedron : TETRAHEDRA) {
                            polygonize(cube[tetrahedron[0]], cube[tetrahedron[1]],
                                    cube[tetrahedron[2]], cube[tetrahedron[3]]);
                        }
                    }
                }
            }
        }

        private FieldVertex fieldVertex(float sourceX, float sourceY, float depthNormal) {
            return new FieldVertex(sourceX, sourceY, depthNormal,
                    field(sourceX, sourceY, depthNormal));
        }

        private float field(float sourceX, float sourceY, float depthNormal) {
            return profileAt(sourceX, sourceY) + profileOffset
                    - depthNormal * depthNormal;
        }

        private void polygonize(FieldVertex first, FieldVertex second,
                                FieldVertex third, FieldVertex fourth) {
            FieldVertex[] vertices = {first, second, third, fourth};
            int[] inside = new int[4];
            int[] outside = new int[4];
            int insideCount = 0;
            int outsideCount = 0;
            for (int index = 0; index < vertices.length; index++) {
                if (vertices[index].value >= 0.0F) {
                    inside[insideCount++] = index;
                } else {
                    outside[outsideCount++] = index;
                }
            }
            if (insideCount == 0 || insideCount == 4) {
                return;
            }
            if (insideCount == 1 || insideCount == 3) {
                boolean oneInside = insideCount == 1;
                int pivot = oneInside ? inside[0] : outside[0];
                int[] others = oneInside ? outside : inside;
                ColoredVertex a = intersection(vertices[pivot], vertices[others[0]]);
                ColoredVertex b = intersection(vertices[pivot], vertices[others[1]]);
                ColoredVertex c = intersection(vertices[pivot], vertices[others[2]]);
                addTriangle(a, b, c);
                return;
            }
            ColoredVertex a = intersection(vertices[inside[0]], vertices[outside[0]]);
            ColoredVertex b = intersection(vertices[inside[0]], vertices[outside[1]]);
            ColoredVertex c = intersection(vertices[inside[1]], vertices[outside[1]]);
            ColoredVertex d = intersection(vertices[inside[1]], vertices[outside[0]]);
            addTriangle(a, b, c);
            addTriangle(a, c, d);
        }

        private ColoredVertex intersection(FieldVertex first, FieldVertex second) {
            float denominator = first.value - second.value;
            float amount = Math.abs(denominator) < 0.00001F
                    ? 0.5F : first.value / denominator;
            amount = clamp(amount, 0.0F, 1.0F);
            float sourceX = lerp(first.sourceX, second.sourceX, amount);
            float sourceY = lerp(first.sourceY, second.sourceY, amount);
            float depthNormal = lerp(first.depthNormal, second.depthNormal, amount);
            Point normal = haloNormal(sourceX, sourceY, depthNormal);
            Point point = new Point(modelX + sourceX / SHAPE[0].length() * modelWidth,
                    modelY + sourceY / SHAPE.length * modelHeight,
                    modelZ + depthNormal * halfDepth);
            return new ColoredVertex(point, normal,
                    haloColor(style, sourceX, sourceY, normal));
        }

        private Point haloNormal(float sourceX, float sourceY, float depthNormal) {
            float sample = 0.08F;
            float gradientX = (field(sourceX + sample, sourceY, depthNormal)
                    - field(sourceX - sample, sourceY, depthNormal)) / (2.0F * sample)
                    * SHAPE[0].length() / modelWidth;
            float gradientY = (field(sourceX, sourceY + sample, depthNormal)
                    - field(sourceX, sourceY - sample, depthNormal)) / (2.0F * sample)
                    * SHAPE.length / modelHeight;
            float gradientZ = -2.0F * depthNormal / halfDepth;
            return normalize(new Point(-gradientX, -gradientY, -gradientZ));
        }

        private static boolean solid(int column, int row) {
            return row >= 0 && row < SHAPE.length
                    && column >= 0 && column < SHAPE[row].length()
                    && SHAPE[row].charAt(column) != '.';
        }

        private static boolean roundedSolid(int column, int row, float threshold) {
            int columns = SHAPE[0].length() * CONTOUR_SCALE;
            int rows = SHAPE.length * CONTOUR_SCALE;
            if (column < 0 || column >= columns || row < 0 || row >= rows) {
                return false;
            }
            float sourceX = (column + 0.5F) / CONTOUR_SCALE;
            float sourceY = (row + 0.5F) / CONTOUR_SCALE;
            return smoothMask(sourceX, sourceY) >= threshold;
        }

        private static float smoothMask(float sourceX, float sourceY) {
            if (sourceX <= 0.0F || sourceX >= SHAPE[0].length()
                    || sourceY <= 0.0F || sourceY >= SHAPE.length) {
                return 0.0F;
            }
            float sampleX = sourceX - 0.5F;
            float sampleY = sourceY - 0.5F;
            int x0 = floor(sampleX);
            int y0 = floor(sampleY);
            float tx = sampleX - x0;
            float ty = sampleY - y0;
            float top = lerp(solidValue(x0, y0), solidValue(x0 + 1, y0), tx);
            float bottom = lerp(solidValue(x0, y0 + 1), solidValue(x0 + 1, y0 + 1), tx);
            return lerp(top, bottom, ty);
        }

        private static float surfaceDepth(float sourceX, float sourceY, float profileOffset) {
            float available = profileAt(sourceX, sourceY) + profileOffset;
            return available <= 0.0F ? 0.0F
                    : MathHelper.sqrt(available);
        }

        private static float[][] createProfile() {
            int columns = SHAPE[0].length() * CONTOUR_SCALE;
            int rows = SHAPE.length * CONTOUR_SCALE;
            boolean[][] inside = new boolean[rows + 1][columns + 1];
            for (int row = 0; row <= rows; row++) {
                for (int column = 0; column <= columns; column++) {
                    inside[row][column] = smoothMask(column / (float) CONTOUR_SCALE,
                            row / (float) CONTOUR_SCALE) >= 0.5F;
                }
            }
            float[][] profile = new float[rows + 1][columns + 1];
            int searchRadius = (int) Math.ceil(PROFILE_RADIUS * CONTOUR_SCALE) + 1;
            for (int row = 0; row <= rows; row++) {
                for (int column = 0; column <= columns; column++) {
                    float closest = PROFILE_RADIUS;
                    for (int offsetY = -searchRadius; offsetY <= searchRadius; offsetY++) {
                        int candidateRow = row + offsetY;
                        if (candidateRow < 0 || candidateRow > rows) {
                            continue;
                        }
                        for (int offsetX = -searchRadius; offsetX <= searchRadius; offsetX++) {
                            int candidateColumn = column + offsetX;
                            if (candidateColumn < 0 || candidateColumn > columns
                                    || inside[candidateRow][candidateColumn] == inside[row][column]) {
                                continue;
                            }
                            float distance = MathHelper.sqrt(offsetX * offsetX + offsetY * offsetY)
                                    / CONTOUR_SCALE;
                            if (distance < closest) {
                                closest = distance;
                            }
                        }
                    }
                    float amount = clamp(closest / PROFILE_RADIUS, 0.0F, 1.0F);
                    profile[row][column] = inside[row][column] ? amount : -amount;
                }
            }
            return profile;
        }

        private static float profileAt(float sourceX, float sourceY) {
            int columns = SHAPE[0].length() * CONTOUR_SCALE;
            int rows = SHAPE.length * CONTOUR_SCALE;
            float gridX = clamp(sourceX * CONTOUR_SCALE, 0.0F, columns);
            float gridY = clamp(sourceY * CONTOUR_SCALE, 0.0F, rows);
            int x0 = Math.min(columns, floor(gridX));
            int y0 = Math.min(rows, floor(gridY));
            int x1 = Math.min(columns, x0 + 1);
            int y1 = Math.min(rows, y0 + 1);
            float tx = gridX - x0;
            float ty = gridY - y0;
            float top = lerp(PROFILE[y0][x0], PROFILE[y0][x1], tx);
            float bottom = lerp(PROFILE[y1][x0], PROFILE[y1][x1], tx);
            return lerp(top, bottom, ty);
        }

        private static float solidValue(int column, int row) {
            return solid(column, row) ? 1.0F : 0.0F;
        }
    }

    private static final class EmissiveGlowRenderer extends ModelRenderer {
        private final float centerX;
        private final float centerY;
        private final float centerZ;
        private final float planarScale;
        private final float depthScale;

        private EmissiveGlowRenderer(OtsutsukiArmorModel model) {
            this(model, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F);
        }

        private EmissiveGlowRenderer(OtsutsukiArmorModel model,
                                     float centerX, float centerY, float centerZ,
                                     float planarScale, float depthScale) {
            super(model);
            this.centerX = centerX;
            this.centerY = centerY;
            this.centerZ = centerZ;
            this.planarScale = planarScale;
            this.depthScale = depthScale;
        }

        @Override
        public void render(float scale) {
            float previousBrightnessX = OpenGlHelper.lastBrightnessX;
            float previousBrightnessY = OpenGlHelper.lastBrightnessY;
            GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT
                    | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LIGHTING_BIT);
            GlStateManager.pushMatrix();
            try {
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
                GL11.glDisable(GL11.GL_LIGHTING);
                GL11.glDepthMask(false);
                GL11.glDepthFunc(GL11.GL_LEQUAL);
                GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                if (planarScale != 1.0F || depthScale != 1.0F) {
                    GlStateManager.translate(centerX * scale, centerY * scale, centerZ * scale);
                    GlStateManager.scale(planarScale, planarScale, depthScale);
                    GlStateManager.translate(-centerX * scale, -centerY * scale, -centerZ * scale);
                }
                OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,
                        240.0F, 240.0F);
                super.render(scale);
            } finally {
                OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,
                        previousBrightnessX, previousBrightnessY);
                GlStateManager.popMatrix();
                GL11.glPopAttrib();
            }
        }
    }

    private static ColorValue orbColor(MeshStyle style, Point normal) {
        float diffuse = clamp(0.5F + normal.x * -0.22F + normal.y * -0.30F
                + normal.z * -0.38F, 0.0F, 1.0F);
        float highlight = (float) Math.pow(diffuse, 8.0D);
        if (style == MeshStyle.ORB_CORE_GLOW) {
            return new ColorValue(1.0F, 0.28F + highlight * 0.18F,
                    0.15F + highlight * 0.08F, 0.10F + highlight * 0.05F);
        }
        if (style == MeshStyle.ORB_RIM_GLOW) {
            float rim = 1.0F - Math.abs(normal.z);
            return new ColorValue(1.0F, 0.24F + highlight * 0.16F,
                    0.18F, 0.16F + rim * 0.16F);
        }
        float red = lerp(0.48F, 0.84F, diffuse) + highlight * 0.12F;
        float green = lerp(0.025F, 0.24F, diffuse) + highlight * 0.18F;
        float blue = lerp(0.07F, 0.13F, diffuse) + highlight * 0.08F;
        return new ColorValue(clamp(red, 0.0F, 1.0F), clamp(green, 0.0F, 1.0F),
                clamp(blue, 0.0F, 1.0F), 1.0F);
    }

    private static ColorValue haloColor(MeshStyle style, float sourceX, float sourceY,
                                        Point normal) {
        ColorValue authored = sampledHaloColor(sourceX, sourceY);
        float front = Math.abs(normal.z);
        float edge = 1.0F - front;
        if (style == MeshStyle.HALO_CORE_GLOW) {
            return new ColorValue(1.0F, 0.34F, 0.62F, 0.12F + front * 0.055F);
        }
        if (style == MeshStyle.HALO_RIM_GLOW) {
            return new ColorValue(1.0F, 0.20F, 0.58F, 0.20F + edge * 0.24F);
        }
        float shade = 0.87F + Math.max(0.0F, -normal.y) * 0.07F + front * 0.06F;
        return new ColorValue(clamp(authored.red * shade, 0.0F, 1.0F),
                clamp(authored.green * shade, 0.0F, 1.0F),
                clamp(authored.blue * shade, 0.0F, 1.0F), 1.0F);
    }

    private static ColorValue sampledHaloColor(float sourceX, float sourceY) {
        float sampleX = sourceX - 0.5F;
        float sampleY = sourceY - 0.5F;
        int x0 = floor(sampleX);
        int y0 = floor(sampleY);
        float tx = sampleX - x0;
        float ty = sampleY - y0;
        float red = 0.0F;
        float green = 0.0F;
        float blue = 0.0F;
        float total = 0.0F;
        for (int offsetY = 0; offsetY <= 1; offsetY++) {
            for (int offsetX = 0; offsetX <= 1; offsetX++) {
                int column = x0 + offsetX;
                int row = y0 + offsetY;
                if (!SmoothHaloBox.solid(column, row)) {
                    continue;
                }
                float weightX = offsetX == 0 ? 1.0F - tx : tx;
                float weightY = offsetY == 0 ? 1.0F - ty : ty;
                float weight = weightX * weightY;
                ColorValue color = haloPalette(SmoothHaloBox.SHAPE[row].charAt(column));
                red += color.red * weight;
                green += color.green * weight;
                blue += color.blue * weight;
                total += weight;
            }
        }
        if (total > 0.0001F) {
            return new ColorValue(red / total, green / total, blue / total, 1.0F);
        }
        return new ColorValue(1.0F, 0.46F, 0.66F, 1.0F);
    }

    private static ColorValue haloPalette(char code) {
        switch (code) {
            case 'H':
                return rgb(0xFFBDD3);
            case 'L':
                return rgb(0xFEA9C6);
            case 'M':
                return rgb(0xF97596);
            default:
                return rgb(0xF44066);
        }
    }

    private static ColorValue rgb(int color) {
        return new ColorValue(((color >>> 16) & 0xFF) / 255.0F,
                ((color >>> 8) & 0xFF) / 255.0F, (color & 0xFF) / 255.0F, 1.0F);
    }

    private static Point subtract(Point first, Point second) {
        return new Point(first.x - second.x, first.y - second.y,
                first.z - second.z);
    }

    private static Point normalize(Point point) {
        float length = MathHelper.sqrt(point.x * point.x + point.y * point.y + point.z * point.z);
        if (length < 0.00001F) {
            return new Point(0.0F, 0.0F, 1.0F);
        }
        return new Point(point.x / length, point.y / length, point.z / length);
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static float lerp(float start, float end, float amount) {
        return start + (end - start) * amount;
    }

    private static final class FieldVertex {
        private final float sourceX;
        private final float sourceY;
        private final float depthNormal;
        private final float value;

        private FieldVertex(float sourceX, float sourceY, float depthNormal, float value) {
            this.sourceX = sourceX;
            this.sourceY = sourceY;
            this.depthNormal = depthNormal;
            this.value = value;
        }
    }

    private static final class TexturedTriangle {
        private final MeshVertex first;
        private final MeshVertex second;
        private final MeshVertex third;
        private final Point normal;

        private TexturedTriangle(MeshVertex first, MeshVertex second,
                                 MeshVertex third, Point normal) {
            this.first = first;
            this.second = second;
            this.third = third;
            this.normal = normal;
        }
    }

    private static final class MeshVertex {
        private final Point point;
        private final float u;
        private final float v;

        private MeshVertex(Point point, float u, float v) {
            this.point = point;
            this.u = u;
            this.v = v;
        }
    }

    private static final class Triangle {
        private final ColoredVertex first;
        private final ColoredVertex second;
        private final ColoredVertex third;

        private Triangle(ColoredVertex first, ColoredVertex second, ColoredVertex third) {
            this.first = first;
            this.second = second;
            this.third = third;
        }
    }

    private static final class ColoredVertex {
        private final Point point;
        private final Point normal;
        private final ColorValue color;

        private ColoredVertex(Point point, Point normal, ColorValue color) {
            this.point = point;
            this.normal = normal;
            this.color = color;
        }
    }

    private static final class ColorValue {
        private final float red;
        private final float green;
        private final float blue;
        private final float alpha;

        private ColorValue(float red, float green, float blue, float alpha) {
            this.red = red;
            this.green = green;
            this.blue = blue;
            this.alpha = alpha;
        }
    }

    private static final class Point {
        private final float x;
        private final float y;
        private final float z;

        private Point(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
