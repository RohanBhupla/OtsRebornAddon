package net.rebornaddon.armor.client;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.util.math.MathHelper;
import net.rebornaddon.armor.OtsutsukiArmorSet;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OtsutsukiArmorTransformTest {
    private static final float EPSILON = 0.0001F;

    @Test
    public void vanillaBonesMatchTheBlockbenchPlayerRig() {
        ModelBiped model = new ModelBiped();
        assertPoint(model.bipedHead.rotationPointX, model.bipedHead.rotationPointY,
                model.bipedHead.rotationPointZ, 0.0F, 0.0F, 0.0F);
        assertPoint(model.bipedBody.rotationPointX, model.bipedBody.rotationPointY,
                model.bipedBody.rotationPointZ, 0.0F, 0.0F, 0.0F);
        assertPoint(model.bipedRightArm.rotationPointX, model.bipedRightArm.rotationPointY,
                model.bipedRightArm.rotationPointZ, -5.0F, 2.0F, 0.0F);
        assertPoint(model.bipedLeftArm.rotationPointX, model.bipedLeftArm.rotationPointY,
                model.bipedLeftArm.rotationPointZ, 5.0F, 2.0F, 0.0F);
        assertPoint(model.bipedRightLeg.rotationPointX, model.bipedRightLeg.rotationPointY,
                model.bipedRightLeg.rotationPointZ, -1.9F, 12.0F, 0.0F);
        assertPoint(model.bipedLeftLeg.rotationPointX, model.bipedLeftLeg.rotationPointY,
                model.bipedLeftLeg.rotationPointZ, 1.9F, 12.0F, 0.0F);
    }

    @Test
    public void cubesUseBlockbenchForge112Coordinates() {
        assertPoint(OtsutsukiArmorModel.cubeX(0.0F, 4.0F),
                OtsutsukiArmorModel.cubeY(24.0F, 32.0F),
                OtsutsukiArmorModel.cubeZ(0.0F, -4.0F),
                -4.0F, -8.0F, -4.0F);
        assertPoint(OtsutsukiArmorModel.cubeX(5.0F, 8.0F),
                OtsutsukiArmorModel.cubeY(22.0F, 24.0F),
                OtsutsukiArmorModel.cubeZ(0.0F, -2.0F),
                -3.0F, -2.0F, -2.0F);
    }

    @Test
    public void nestedPivotsAndRotationsUseBlockbenchExporterSigns() {
        assertPoint(OtsutsukiArmorModel.pivotX(5.0F, 0.0F),
                OtsutsukiArmorModel.pivotY(22.0F, 24.0F),
                OtsutsukiArmorModel.pivotZ(0.0F, 0.0F),
                -5.0F, 2.0F, 0.0F);
        assertEquals((float) Math.toRadians(-12.5D),
                OtsutsukiArmorModel.rotationX(12.5F), EPSILON);
        assertEquals((float) Math.toRadians(7.5D),
                OtsutsukiArmorModel.rotationY(-7.5F), EPSILON);
        assertEquals((float) Math.toRadians(-17.5D),
                OtsutsukiArmorModel.rotationZ(-17.5F), EPSILON);
    }

    @Test
    public void centeredOrbPivotPreservesItsAuthoredPlacement() {
        assertCenteredPlacement(-2.0F, 4.0F);
        assertCenteredPlacement(9.0F, 4.0F);
        assertCenteredPlacement(-11.0F, 4.0F);
    }

    @Test
    public void orbSlotsSortClockwiseInModelScreenSpace() {
        assertEquals(0.0F,
                OtsutsukiArmorModel.clockwiseAngle(1.0F, 0.0F, 0.0F, 0.0F), EPSILON);
        assertEquals((float) Math.PI * 0.5F,
                OtsutsukiArmorModel.clockwiseAngle(0.0F, 1.0F, 0.0F, 0.0F), EPSILON);
    }

    @Test
    public void oversizedGarmentInflationIsReducedSelectively() {
        assertEquals(0.65F, OtsutsukiArmorModel.adjustedInflate(
                "right arm armor", 1.0F, 5.0F, 5.0F), EPSILON);
        assertEquals(0.82F, OtsutsukiArmorModel.adjustedInflate(
                "right arm armor", 1.0F, 4.0F, 4.0F), EPSILON);
        assertEquals(0.55F, OtsutsukiArmorModel.adjustedInflate(
                "right leg armor", 1.0F, 5.0F, 6.0F), EPSILON);
        assertEquals(0.85F, OtsutsukiArmorModel.adjustedInflate(
                "helmet", 1.0F, 8.0F, 8.0F), EPSILON);
        assertEquals(0.0F, OtsutsukiArmorModel.adjustedInflate(
                "right horn", 0.0F, 2.0F, 0.0F), EPSILON);
        assertEquals(0.42F, OtsutsukiArmorModel.adjustedInflate(
                OtsutsukiArmorSet.KAGUYA, "right arm armor", 1.0F, 5.0F, 5.0F), EPSILON);
        assertEquals(0.16F, OtsutsukiArmorModel.adjustedInflate(
                OtsutsukiArmorSet.KAGUYA, "chestplate", 1.01F, 8.0F, 4.0F), EPSILON);
        assertEquals(0.35F, OtsutsukiArmorModel.adjustedInflate(
                OtsutsukiArmorSet.KAGUYA, "right leg armor", 1.0F, 5.0F, 6.0F), EPSILON);
        assertEquals(0.56F, OtsutsukiArmorModel.adjustedInflate(
                OtsutsukiArmorSet.KINSHIKI, "right arm armor lower", 0.75F,
                4.0F, 4.0F), EPSILON);
    }

    @Test
    public void longHairAndBeardsUseDifferentSafePitchLimits() {
        float severePitch = (float) Math.toRadians(80.0D);
        assertEquals((float) Math.toRadians(18.0D),
                OtsutsukiArmorModel.constrainedLongHairPitch(severePitch), EPSILON);
        assertEquals((float) Math.toRadians(12.0D),
                OtsutsukiArmorModel.constrainedBeardPitch(severePitch), EPSILON);
        float severeUpwardPitch = -severePitch;
        assertEquals(0.0F,
                OtsutsukiArmorModel.constrainedLongHairPitch(severeUpwardPitch), EPSILON);
        assertEquals((float) Math.toRadians(-30.0D),
                OtsutsukiArmorModel.constrainedBeardPitch(severeUpwardPitch), EPSILON);
    }

    @Test
    public void articulatedHairAnchorsToTheEdgeNearestTheHead() {
        assertEquals(1.0F, OtsutsukiArmorModel.nearestAttachmentEdge(1.0F, 25.0F),
                EPSILON);
        assertEquals(-2.0F, OtsutsukiArmorModel.nearestAttachmentEdge(-12.0F, -2.0F),
                EPSILON);
        assertEquals(3.0F, OtsutsukiArmorModel.nearestAttachmentEdge(3.0F, 10.0F),
                EPSILON);
    }

    @Test
    public void robeWaistStaysAnchoredWhileTheHemFollowsLegsWithinAClothLimit() {
        assertEquals(0.0F, OtsutsukiArmorModel.robeMotionWeight(0.0F), EPSILON);
        assertTrue(OtsutsukiArmorModel.robeMotionWeight(0.12F) > 0.0F);
        assertTrue(OtsutsukiArmorModel.robeMotionWeight(0.26F)
                > OtsutsukiArmorModel.robeMotionWeight(0.12F));
        assertTrue(OtsutsukiArmorModel.robeMotionWeight(0.5F) > 0.0F);
        assertEquals(1.0F, OtsutsukiArmorModel.robeMotionWeight(0.55F), EPSILON);
        assertEquals(1.0F, OtsutsukiArmorModel.robeMotionWeight(1.0F), EPSILON);
        assertEquals(7.0F, OtsutsukiArmorModel.robeProjectedLegShift(1.2F, 40.0F),
                EPSILON);
        assertEquals(-7.0F, OtsutsukiArmorModel.robeProjectedLegShift(-1.2F, 40.0F),
                EPSILON);
        assertTrue(OtsutsukiArmorModel.robeProjectedLegShift(1.0F, 13.2F) < 6.0F);
        assertTrue(OtsutsukiArmorModel.robeSilhouetteComponent(0.707F, 0.0F)
                > OtsutsukiArmorModel.robeSilhouetteComponent(0.707F, 1.0F));
        assertTrue(OtsutsukiArmorModel.robeSilhouetteComponent(0.707F, 0.0F, 0.14F)
                > OtsutsukiArmorModel.robeSilhouetteComponent(0.707F, 0.0F));
        assertEquals(0.707F,
                OtsutsukiArmorModel.robeSilhouetteComponent(0.707F, 1.0F, 0.14F),
                EPSILON);
    }

    @Test
    public void coveredBootSwingStaysCenteredUnderTheMovingRobeHem() {
        ModelBiped model = new ModelBiped();
        float bodyPitch = 0.42F;
        float sourceLegPitch = 1.05F;
        float oppositeLegPitch = -1.05F;
        model.bipedBody.rotateAngleX = bodyPitch;
        model.bipedBody.rotationPointY = 1.5F;
        model.bipedBody.rotationPointZ = -0.4F;
        float coveredInflate = OtsutsukiArmorModel.coveredBootInflate(1.0F);
        float bootCenterY = OtsutsukiArmorModel.coveredBootCenterY(
                12.0F, coveredInflate);
        float bootHalfHeight = OtsutsukiArmorModel.coveredBootHalfHeight(
                12.0F, coveredInflate);

        OtsutsukiArmorModel.positionBootUnderRobe(model.bipedRightLeg,
                model.bipedBody, sourceLegPitch, oppositeLegPitch,
                RobeMovementCompatibility.RIGHT_LEG_X,
                bootCenterY, bootHalfHeight);

        float targetY = OtsutsukiArmorModel.robeBootTargetY(
                sourceLegPitch, bootHalfHeight);
        float targetZ = OtsutsukiArmorModel.robeBootTargetZ(
                sourceLegPitch, oppositeLegPitch);
        float expectedWorldY = model.bipedBody.rotationPointY
                + targetY * MathHelper.cos(bodyPitch)
                - targetZ * MathHelper.sin(bodyPitch);
        float expectedWorldZ = model.bipedBody.rotationPointZ
                + targetY * MathHelper.sin(bodyPitch)
                + targetZ * MathHelper.cos(bodyPitch);
        float actualCenterY = model.bipedRightLeg.rotationPointY
                + bootCenterY * MathHelper.cos(model.bipedRightLeg.rotateAngleX);
        float actualCenterZ = model.bipedRightLeg.rotationPointZ
                + bootCenterY * MathHelper.sin(model.bipedRightLeg.rotateAngleX);

        assertEquals(expectedWorldY, actualCenterY, EPSILON);
        assertEquals(expectedWorldZ, actualCenterZ, EPSILON);
        assertEquals(bodyPitch + OtsutsukiArmorModel.robeBootSwing(sourceLegPitch),
                model.bipedRightLeg.rotateAngleX, EPSILON);
        assertTrue(OtsutsukiArmorModel.robeBootSwing(sourceLegPitch) > 0.0F);
        assertTrue(OtsutsukiArmorModel.robeBootSwing(sourceLegPitch)
                < sourceLegPitch);
    }

    @Test
    public void coveredBootAnchorUsesEachSetsAuthoredInflation() {
        assertEquals(0.45F, OtsutsukiArmorModel.coveredBootInflate(1.0F), EPSILON);
        assertEquals(0.4F, OtsutsukiArmorModel.coveredBootInflate(0.4F), EPSILON);
        assertEquals(10.305F,
                OtsutsukiArmorModel.coveredBootCenterY(12.0F, 0.45F), EPSILON);
        assertEquals(2.145F,
                OtsutsukiArmorModel.coveredBootHalfHeight(12.0F, 0.45F), EPSILON);
        assertEquals(10.28F,
                OtsutsukiArmorModel.coveredBootCenterY(12.0F, 0.4F), EPSILON);
        assertEquals(2.12F,
                OtsutsukiArmorModel.coveredBootHalfHeight(12.0F, 0.4F), EPSILON);
    }

    @Test
    public void opposingBootsStayInsideTheRobeInsteadOfReachingItsOuterEdges() {
        float outerRightEdge = OtsutsukiArmorModel.robeProjectedLegShift(
                1.2F, 13.2F);
        float outerLeftEdge = OtsutsukiArmorModel.robeProjectedLegShift(
                -1.2F, 13.2F);
        float rightBoot = OtsutsukiArmorModel.robeBootTargetZ(1.2F, -1.2F);
        float leftBoot = OtsutsukiArmorModel.robeBootTargetZ(-1.2F, 1.2F);

        assertTrue(rightBoot > 0.0F);
        assertTrue(rightBoot < outerRightEdge * 0.6F);
        assertTrue(leftBoot < 0.0F);
        assertTrue(leftBoot > outerLeftEdge * 0.6F);
        assertEquals(outerRightEdge,
                OtsutsukiArmorModel.robeBootTargetZ(1.2F, 1.2F), EPSILON);
    }

    @Test
    public void longRobeLegsAreCompletelyFrozenInTheirStandingPose() {
        ModelBiped model = new ModelBiped();
        model.bipedRightLeg.rotateAngleX = 1.4F;
        model.bipedRightLeg.rotateAngleY = 0.4F;
        model.bipedRightLeg.rotateAngleZ = -0.3F;
        model.bipedRightLeg.rotationPointY = 9.0F;
        model.bipedRightLeg.rotationPointZ = 4.0F;
        model.bipedLeftLeg.rotateAngleX = -1.4F;
        model.bipedLeftLeg.rotateAngleY = -0.4F;
        model.bipedLeftLeg.rotateAngleZ = 0.3F;
        RobeMovementCompatibility.freezeLegGeometry(model);

        assertPoint(model.bipedRightLeg.rotateAngleX, model.bipedRightLeg.rotateAngleY,
                model.bipedRightLeg.rotateAngleZ, 0.0F, 0.0F, 0.0F);
        assertPoint(model.bipedLeftLeg.rotateAngleX, model.bipedLeftLeg.rotateAngleY,
                model.bipedLeftLeg.rotateAngleZ, 0.0F, 0.0F, 0.0F);
        assertPoint(model.bipedRightLeg.rotationPointX, model.bipedRightLeg.rotationPointY,
                model.bipedRightLeg.rotationPointZ, -1.9F, 12.0F, 0.0F);
        assertPoint(model.bipedLeftLeg.rotationPointX, model.bipedLeftLeg.rotationPointY,
                model.bipedLeftLeg.rotationPointZ, 1.9F, 12.0F, 0.0F);
    }

    @Test
    public void floorLengthRobesHideBothPlayerLegLayers() {
        ModelPlayer model = new ModelPlayer(0.0F, false);
        RobeMovementCompatibility.hidePlayerLegGeometry(model);
        assertFalse(model.bipedRightLeg.showModel);
        assertFalse(model.bipedLeftLeg.showModel);
        assertFalse(model.bipedRightLegwear.showModel);
        assertFalse(model.bipedLeftLegwear.showModel);
    }

    @Test
    public void robeHookDoesNotFreezeSeparateArmorOrBootModels() {
        assertTrue(RobeMovementCompatibility.isPlayerBodyModel(
                new ModelPlayer(0.0F, false)));
        assertFalse(RobeMovementCompatibility.isPlayerBodyModel(new ModelBiped()));
    }

    @Test
    public void everyBootUsesRobeAwareGeometryWithoutChangingItsNormalModel() throws IOException {
        for (OtsutsukiArmorSet armorSet : OtsutsukiArmorSet.values()) {
            Path path = Paths.get("src/main/resources/assets/rebornaddon/armor_models/otsutsuki",
                    armorSet.id() + "_main.json");
            try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                OtsutsukiArmorModel model = OtsutsukiArmorModel.load(
                        armorSet, EntityEquipmentSlot.FEET, reader);
                assertEquals(armorSet.id(), 2,
                        boxesOfType(model.bipedRightLeg, "AdaptiveBootBox")
                                + boxesOfType(model.bipedLeftLeg, "AdaptiveBootBox"));
            }
        }
    }

    @Test
    public void kinshikiHaloMeshMatchesTheAuthoredDisconnectedSilhouette() throws IOException {
        Path texturePath = Paths.get("src/main/resources/assets/rebornaddon/textures/models/armor/otsutsuki",
                "kinshiki_main.png");
        BufferedImage texture = ImageIO.read(texturePath.toFile());
        int opaquePixels = 0;
        for (int row = 0; row < 25; row++) {
            for (int column = 0; column < 18; column++) {
                boolean opaque = (texture.getRGB(42 + column, 32 + row) >>> 24) != 0;
                if (opaque) {
                    opaquePixels++;
                }
                assertEquals("Halo mask differs at " + column + "," + row,
                        opaque, OtsutsukiArmorModel.kinshikiHaloPixel(column, row));
            }
        }
        assertEquals(170, opaquePixels);
        assertFalse(OtsutsukiArmorModel.kinshikiHaloPixel(8, 24));
        assertFalse(OtsutsukiArmorModel.kinshikiHaloPixel(9, 24));
        int contourScale = OtsutsukiArmorModel.kinshikiHaloContourScale();
        assertFalse(OtsutsukiArmorModel.kinshikiRoundedHaloPixel(5 * contourScale, 0));
        assertTrue(OtsutsukiArmorModel.kinshikiRoundedHaloPixel(
                5 * contourScale + contourScale / 2, contourScale / 2));
        for (int row = 21 * contourScale; row < 25 * contourScale; row++) {
            for (int column = 8 * contourScale; column < 10 * contourScale; column++) {
                assertFalse("Rounded contour closed the lower halo gap",
                        OtsutsukiArmorModel.kinshikiRoundedHaloPixel(column, row));
            }
        }
        assertEquals(0.0F, OtsutsukiArmorModel.kinshikiHaloSurfaceDepth(8.5F, 24.5F),
                EPSILON);
        float roundedEdgeDepth = OtsutsukiArmorModel.kinshikiHaloSurfaceDepth(5.0F, 0.5F);
        float centerDepth = OtsutsukiArmorModel.kinshikiHaloSurfaceDepth(5.5F, 0.5F);
        assertTrue("Halo edge should curve smoothly out of its silhouette",
                roundedEdgeDepth > 0.0F && roundedEdgeDepth < centerDepth);
        assertTrue("Halo center should be curved instead of a flat extrusion",
                centerDepth > 0.35F && centerDepth < 0.90F);
    }

    @Test
    public void kaguyaChestMarkingsAreThreeOnePixelSquares() throws IOException {
        Path texturePath = Paths.get(
                "src/main/resources/assets/rebornaddon/textures/models/armor/otsutsuki",
                "kaguya_main.png");
        BufferedImage texture = ImageIO.read(texturePath.toFile());
        int[] rows = {26, 29, 32};
        for (int row : rows) {
            assertTrue(isDark(texture, 47, row));
            assertFalse(isDark(texture, 48, row));
            assertFalse(isDark(texture, 47, row - 1));
            assertFalse(isDark(texture, 48, row - 1));
        }
    }

    @Test
    public void everyAuthoredOrbBuildsItsBaseAndBothGlowMeshes() throws IOException {
        for (OtsutsukiArmorSet armorSet : OtsutsukiArmorSet.values()) {
            Path path = Paths.get("src/main/resources/assets/rebornaddon/armor_models/otsutsuki",
                    armorSet.id() + "_main.json");
            String json = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            int expectedOrbs = occurrences(json, "\"name\":\"Orb_");
            try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                OtsutsukiArmorModel model = OtsutsukiArmorModel.load(EntityEquipmentSlot.CHEST, reader);
                int sphereMeshes = boxesOfType(model.bipedBody, "SmoothSphereBox")
                        + boxesOfType(model.bipedRightArm, "SmoothSphereBox")
                        + boxesOfType(model.bipedLeftArm, "SmoothSphereBox");
                assertEquals(armorSet.id() + " lost an authored orb renderer",
                        expectedOrbs * 3, sphereMeshes);
            }
        }
    }

    @Test
    public void kinshikiRuntimeHaloUsesThreeAuthoredSilhouettePasses() throws IOException {
        Path path = Paths.get("src/main/resources/assets/rebornaddon/armor_models/otsutsuki",
                "kinshiki_main.json");
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            OtsutsukiArmorModel model = OtsutsukiArmorModel.load(
                    OtsutsukiArmorSet.KINSHIKI, EntityEquipmentSlot.CHEST, reader);
            assertEquals(3, boxesOfType(model.bipedBody, "SmoothHaloBox"));
            assertEquals(0, boxesOfType(model.bipedBody, "RoundedHaloTubeBox"));
        }
    }

    @Test
    public void everyRuntimeSlotBuildsGeometryOnItsVisibleBones() throws IOException {
        for (OtsutsukiArmorSet armorSet : OtsutsukiArmorSet.values()) {
            for (EntityEquipmentSlot slot : new EntityEquipmentSlot[]{
                    EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST,
                    EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET}) {
                String variant = slot == EntityEquipmentSlot.LEGS ? "leggings" : "main";
                Path path = Paths.get("src/main/resources/assets/rebornaddon/armor_models/otsutsuki",
                        armorSet.id() + "_" + variant + ".json");
                try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    OtsutsukiArmorModel model = OtsutsukiArmorModel.load(
                            armorSet, slot, reader);
                    int boxes = slotBoxes(model, slot);
                    assertTrue(armorSet.id() + " " + slot.getName() + " did not build any geometry",
                            boxes > 0 || model.hasRenderableGeometry());
                }
            }
        }
    }

    @Test
    public void isshikiRuntimeUsesOpenRolledCollarWithoutExtraCuffBoxes() throws IOException {
        Path path = Paths.get("src/main/resources/assets/rebornaddon/armor_models/otsutsuki",
                "isshiki_main.json");
        OtsutsukiArmorModel neutral;
        OtsutsukiArmorModel runtime;
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            neutral = OtsutsukiArmorModel.load(EntityEquipmentSlot.CHEST, reader);
        }
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            runtime = OtsutsukiArmorModel.load(OtsutsukiArmorSet.ISSHIKI,
                    EntityEquipmentSlot.CHEST, reader);
        }
        assertEquals(slotBoxes(neutral, EntityEquipmentSlot.CHEST),
                slotBoxes(runtime, EntityEquipmentSlot.CHEST));
        assertEquals(1, boxesOfType(runtime.bipedBody, "OpenRolledCollarBox"));
        Object collar = firstBoxOfType(runtime.bipedBody, "OpenRolledCollarBox");
        assertEquals("ColoredMeshBox", collar.getClass().getSuperclass().getSimpleName());

        float elementY = OtsutsukiArmorModel.cubeY(0.25F, 25.25F);
        float elementPivotY = OtsutsukiArmorModel.pivotY(0.25F, 24.0F);
        float collarWorldY = elementPivotY
                + OtsutsukiArmorModel.isshikiCollarCenterY(elementY, 3.0F);
        float elementZ = OtsutsukiArmorModel.cubeZ(-0.25F, -3.25F);
        float elementPivotZ = OtsutsukiArmorModel.pivotZ(-0.25F, 0.0F);
        float collarFrontZ = elementPivotZ
                + OtsutsukiArmorModel.isshikiCollarFrontZ(elementZ);
        float collarBackBaseZ = elementPivotZ
                + OtsutsukiArmorModel.isshikiCollarBackBaseZ(elementZ, 8.0F);
        float collarBackEdgeZ = collarBackBaseZ
                + OtsutsukiArmorModel.isshikiCollarBackDepth(8.0F);
        float collarShoulderX = OtsutsukiArmorModel.isshikiCollarSideX(12.0F);

        assertEquals(-1.49F, collarWorldY, EPSILON);
        assertTrue(collarFrontZ < -3.01F);
        assertEquals(0.19F, collarBackBaseZ, EPSILON);
        assertEquals(1.63F, collarBackEdgeZ, EPSILON);
        assertEquals(3.6F, collarShoulderX, EPSILON);
        assertEquals(1.28F, OtsutsukiArmorModel.isshikiCollarFrontDrop(), EPSILON);
        assertEquals(0.0F,
                OtsutsukiArmorModel.isshikiCollarFrontDropProgress(0.0F), EPSILON);
        assertEquals(0.0F,
                OtsutsukiArmorModel.isshikiCollarFrontDropProgress(0.65F), EPSILON);
        assertTrue(OtsutsukiArmorModel.isshikiCollarFrontDropProgress(0.75F) < 0.25F);
        assertEquals(1.0F,
                OtsutsukiArmorModel.isshikiCollarFrontDropProgress(1.0F), EPSILON);
        assertEquals(0.74F, OtsutsukiArmorModel.isshikiCollarOuterRadius(), EPSILON);
        assertEquals(0.84F, OtsutsukiArmorModel.isshikiCollarInnerRadius(), EPSILON);
        assertTrue(OtsutsukiArmorModel.isshikiCollarInnerRadius()
                > OtsutsukiArmorModel.isshikiCollarOuterRadius());
    }

    @Test
    public void primarySleevesUseFractionalShoulderOverlapOnEverySet() throws IOException {
        for (OtsutsukiArmorSet armorSet : OtsutsukiArmorSet.values()) {
            Path path = Paths.get("src/main/resources/assets/rebornaddon/armor_models/otsutsuki",
                    armorSet.id() + "_main.json");
            try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                OtsutsukiArmorModel model = OtsutsukiArmorModel.load(
                        armorSet, EntityEquipmentSlot.CHEST, reader);
                assertEquals(armorSet.id(), "TexturedPrismBox",
                        boxTypeForName(model.bipedRightArm, "Right Arm Armor"));
                assertEquals(armorSet.id(), "TexturedPrismBox",
                        boxTypeForName(model.bipedLeftArm, "Left Arm Armor"));
            }
        }
    }

    @Test
    public void kaguyasLongBackHairBelongsToTheHeadBone() throws IOException {
        Path path = Paths.get("src/main/resources/assets/rebornaddon/armor_models/otsutsuki",
                "kaguya_main.json");
        OtsutsukiArmorModel head;
        OtsutsukiArmorModel chest;
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            head = OtsutsukiArmorModel.load(OtsutsukiArmorSet.KAGUYA,
                    EntityEquipmentSlot.HEAD, reader);
        }
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            chest = OtsutsukiArmorModel.load(OtsutsukiArmorSet.KAGUYA,
                    EntityEquipmentSlot.CHEST, reader);
        }
        assertEquals(1, boxesNamed(head.bipedHead, "HairBack"));
        assertEquals(0, boxesNamed(chest.bipedBody, "HairBack"));
    }

    @Test
    public void kaguyaRobeUsesOneConnectedAnimatedCircularGarment() throws IOException {
        Path path = Paths.get("src/main/resources/assets/rebornaddon/armor_models/otsutsuki",
                "kaguya_leggings.json");
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            OtsutsukiArmorModel model = OtsutsukiArmorModel.load(
                    OtsutsukiArmorSet.KAGUYA, EntityEquipmentSlot.LEGS, reader);
            assertEquals(1, renderersOfType(model.bipedBody, "CircularRobeRenderer"));
            assertEquals(0, renderersOfType(model.bipedRightLeg, "CircularRobeRenderer")
                    + renderersOfType(model.bipedLeftLeg, "CircularRobeRenderer"));
        }
    }

    @Test
    public void hagoromoSkirtUsesOneConnectedAnimatedCircularGarment() throws IOException {
        Path path = Paths.get("src/main/resources/assets/rebornaddon/armor_models/otsutsuki",
                "hagoromo_leggings.json");
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            OtsutsukiArmorModel model = OtsutsukiArmorModel.load(
                    OtsutsukiArmorSet.HAGOROMO, EntityEquipmentSlot.LEGS, reader);
            assertEquals(1, renderersOfType(model.bipedBody, "CircularRobeRenderer"));
            assertEquals(0, renderersOfType(model.bipedRightLeg, "CircularRobeRenderer")
                    + renderersOfType(model.bipedLeftLeg, "CircularRobeRenderer"));
            assertEquals(0.38F, OtsutsukiArmorModel.adjustedInflate(
                    OtsutsukiArmorSet.HAGOROMO, "dress front", 1.02F,
                    6.0F, 4.0F), EPSILON);
        }
    }

    @Test
    public void hagoromoUsesARoundedBodiceWhileKaguyaKeepsHerAuthoredChestPixels() throws IOException {
        Path hagoromoPath = Paths.get(
                "src/main/resources/assets/rebornaddon/armor_models/otsutsuki",
                "hagoromo_main.json");
        try (BufferedReader reader = Files.newBufferedReader(hagoromoPath,
                StandardCharsets.UTF_8)) {
            OtsutsukiArmorModel model = OtsutsukiArmorModel.load(
                    OtsutsukiArmorSet.HAGOROMO, EntityEquipmentSlot.CHEST, reader);
            assertEquals(1, boxesOfType(model.bipedBody, "RobeBodiceBox"));
        }
        Path kaguyaPath = Paths.get(
                "src/main/resources/assets/rebornaddon/armor_models/otsutsuki",
                "kaguya_main.json");
        try (BufferedReader reader = Files.newBufferedReader(kaguyaPath,
                StandardCharsets.UTF_8)) {
            OtsutsukiArmorModel model = OtsutsukiArmorModel.load(
                    OtsutsukiArmorSet.KAGUYA, EntityEquipmentSlot.CHEST, reader);
            assertEquals(0, boxesOfType(model.bipedBody, "RobeBodiceBox"));
            assertEquals("ModelBox", boxTypeForName(model.bipedBody, "Chestplate"));
        }
    }

    @Test
    public void kaguyaSleevesUseTaperedBodiesAndSeparateStructuredCuffs() throws IOException {
        Path path = Paths.get("src/main/resources/assets/rebornaddon/armor_models/otsutsuki",
                "kaguya_main.json");
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            OtsutsukiArmorModel model = OtsutsukiArmorModel.load(
                    OtsutsukiArmorSet.KAGUYA, EntityEquipmentSlot.CHEST, reader);
            assertEquals(4, boxesOfType(model.bipedRightArm, "TexturedPrismBox")
                    + boxesOfType(model.bipedLeftArm, "TexturedPrismBox"));
        }
    }

    @Test
    public void toneriUpperClothUsesSeparateCurvedCollarAndSashMeshes() throws IOException {
        Path path = Paths.get("src/main/resources/assets/rebornaddon/armor_models/otsutsuki",
                "toneri_main.json");
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            OtsutsukiArmorModel model = OtsutsukiArmorModel.load(
                    OtsutsukiArmorSet.TONERI, EntityEquipmentSlot.CHEST, reader);
            assertEquals(1, boxesOfType(model.bipedBody, "ToneriCollarPanelBox"));
            assertEquals(1, boxesOfType(model.bipedBody, "ToneriSashPanelBox"));
            assertEquals(0, boxesOfType(model.bipedBody, "TexturedPrismBox"));
        }
    }

    private static int slotBoxes(ModelBiped model, EntityEquipmentSlot slot) {
        switch (slot) {
            case HEAD:
                return boxes(model.bipedHead);
            case CHEST:
                return boxes(model.bipedBody) + boxes(model.bipedRightArm)
                        + boxes(model.bipedLeftArm);
            case LEGS:
                return boxes(model.bipedBody) + boxes(model.bipedRightLeg)
                        + boxes(model.bipedLeftLeg);
            case FEET:
                return boxes(model.bipedRightLeg) + boxes(model.bipedLeftLeg);
            default:
                return 0;
        }
    }

    private static int boxes(ModelRenderer renderer) {
        int count = renderer.cubeList.size();
        if (renderer.childModels != null) {
            for (ModelRenderer child : renderer.childModels) {
                count += boxes(child);
            }
        }
        return count;
    }

    private static int boxesOfType(ModelRenderer renderer, String simpleName) {
        int count = 0;
        for (Object box : renderer.cubeList) {
            if (box.getClass().getSimpleName().equals(simpleName)) {
                count++;
            }
        }
        if (renderer.childModels != null) {
            for (ModelRenderer child : renderer.childModels) {
                count += boxesOfType(child, simpleName);
            }
        }
        return count;
    }

    private static Object firstBoxOfType(ModelRenderer renderer, String simpleName) {
        for (Object box : renderer.cubeList) {
            if (box.getClass().getSimpleName().equals(simpleName)) {
                return box;
            }
        }
        if (renderer.childModels != null) {
            for (ModelRenderer child : renderer.childModels) {
                Object found = firstBoxOfType(child, simpleName);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static int boxesNamed(ModelRenderer renderer, String boxName) {
        int count = 0;
        for (Object value : renderer.cubeList) {
            net.minecraft.client.model.ModelBox box =
                    (net.minecraft.client.model.ModelBox) value;
            if (boxName.equals(box.boxName)) count++;
        }
        if (renderer.childModels != null) {
            for (ModelRenderer child : renderer.childModels) {
                count += boxesNamed(child, boxName);
            }
        }
        return count;
    }

    private static String boxTypeForName(ModelRenderer renderer, String boxName) {
        for (Object value : renderer.cubeList) {
            net.minecraft.client.model.ModelBox box =
                    (net.minecraft.client.model.ModelBox) value;
            if (boxName.equals(box.boxName)) return box.getClass().getSimpleName();
        }
        if (renderer.childModels != null) {
            for (ModelRenderer child : renderer.childModels) {
                String found = boxTypeForName(child, boxName);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static int renderersOfType(ModelRenderer renderer, String simpleName) {
        int count = renderer.getClass().getSimpleName().equals(simpleName) ? 1 : 0;
        if (renderer.childModels != null) {
            for (ModelRenderer child : renderer.childModels) {
                count += renderersOfType(child, simpleName);
            }
        }
        return count;
    }

    private static int occurrences(String value, String search) {
        int count = 0;
        int index = 0;
        while ((index = value.indexOf(search, index)) >= 0) {
            count++;
            index += search.length();
        }
        return count;
    }

    private static boolean isDark(BufferedImage image, int x, int y) {
        int color = image.getRGB(x, y);
        return ((color >>> 24) & 0xFF) > 0
                && ((color >>> 16) & 0xFF) < 64
                && ((color >>> 8) & 0xFF) < 64
                && (color & 0xFF) < 64;
    }

    private static void assertPoint(float x, float y, float z,
                                    float expectedX, float expectedY, float expectedZ) {
        assertEquals(expectedX, x, EPSILON);
        assertEquals(expectedY, y, EPSILON);
        assertEquals(expectedZ, z, EPSILON);
    }

    private static void assertCenteredPlacement(float originalMinimum, float size) {
        float center = OtsutsukiArmorModel.centerPivot(originalMinimum, size);
        float centeredMinimum = OtsutsukiArmorModel.centeredMinimum(size);
        assertEquals(originalMinimum, center + centeredMinimum, EPSILON);
    }

}
