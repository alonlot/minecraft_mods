package com.hogwartsmagic.client;

import com.hogwartsmagic.entity.StudentEntity;
import com.hogwartsmagic.entity.StudentModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;

public class StudentRenderer extends MobRenderer<StudentEntity, StudentModel<StudentEntity>> {

    // House textures - one for each Hogwarts house
    private static final ResourceLocation GRYFFINDOR = ResourceLocation.fromNamespaceAndPath("hogwartsmagic", "textures/entity/student_gryffindor.png");
    private static final ResourceLocation HUFFLEPUFF = ResourceLocation.fromNamespaceAndPath("hogwartsmagic", "textures/entity/student_hufflepuff.png");
    private static final ResourceLocation RAVENCLAW = ResourceLocation.fromNamespaceAndPath("hogwartsmagic", "textures/entity/student_ravenclaw.png");
    private static final ResourceLocation SLYTHERIN = ResourceLocation.fromNamespaceAndPath("hogwartsmagic", "textures/entity/student_slytherin.png");

    public StudentRenderer(EntityRendererProvider.Context context) {
        super(context, new StudentModel<>(context.bakeLayer(StudentModel.LAYER_LOCATION)), 0.5f);
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }

    @Override
    public void render(StudentEntity student, float p_114486_, float p_114487_, com.mojang.blaze3d.vertex.PoseStack p_114488_, net.minecraft.client.renderer.MultiBufferSource p_114489_, int p_114490_) {
        if (student.isFlying()) {
            return; // Don't render while flying (invisible effect)
        }
        super.render(student, p_114486_, p_114487_, p_114488_, p_114489_, p_114490_);
    }

    @Override
    public ResourceLocation getTextureLocation(StudentEntity entity) {
        // Return texture based on house
        return switch (entity.getHouseId()) {
            case StudentEntity.HOUSE_HUFFLEPUFF -> HUFFLEPUFF;
            case StudentEntity.HOUSE_RAVENCLAW -> RAVENCLAW;
            case StudentEntity.HOUSE_SLYTHERIN -> SLYTHERIN;
            default -> GRYFFINDOR;
        };
    }
}
