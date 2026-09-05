package net.rebornaddon.armor;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.World;
import net.minecraftforge.common.util.EnumHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.rebornaddon.RebornAddonMod;
import net.rebornaddon.armor.client.OtsutsukiArmorModels;

public final class ItemOtsutsukiCosmeticArmor extends ItemArmor {
    private static final ArmorMaterial COSMETIC = EnumHelper.addArmorMaterial(
            "REBORNADDON_OTSUTSUKI_COSMETIC",
            RebornAddonMod.MODID + ":otsutsuki_cosmetic",
            0,
            new int[]{0, 0, 0, 0},
            0,
            SoundEvents.ITEM_ARMOR_EQUIP_LEATHER,
            0.0F);

    private final OtsutsukiArmorSet armorSet;
    private final String displayName;

    ItemOtsutsukiCosmeticArmor(OtsutsukiArmorSet armorSet, EntityEquipmentSlot slot) {
        super(COSMETIC, 0, slot);
        this.armorSet = armorSet;
        this.displayName = armorSet.itemName(slot);
        String id = armorSet.itemId(slot);
        setRegistryName(RebornAddonMod.MODID, id);
        setUnlocalizedName(RebornAddonMod.MODID + "." + id);
        setCreativeTab(net.narutomod.creativetab.TabModTab.tab);
        setMaxStackSize(1);
        setMaxDamage(0);
        setNoRepair();
    }

    public OtsutsukiArmorSet armorSet() {
        return armorSet;
    }

    @Override
    public Multimap<String, AttributeModifier> getItemAttributeModifiers(EntityEquipmentSlot slot) {
        return ImmutableMultimap.of();
    }

    @Override
    public Multimap<String, AttributeModifier> getAttributeModifiers(EntityEquipmentSlot slot, ItemStack stack) {
        return ImmutableMultimap.of();
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    @Override
    public int getItemEnchantability() {
        return 0;
    }

    @Override
    public int getItemEnchantability(ItemStack stack) {
        return 0;
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        return false;
    }

    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        return false;
    }

    @Override
    public boolean hasEffect(ItemStack stack) {
        return false;
    }

    @Override
    public void onUpdate(ItemStack stack, World world, Entity entity, int itemSlot, boolean selected) {
        removeEnchantments(stack);
        super.onUpdate(stack, world, entity, itemSlot, selected);
    }

    @Override
    public void onArmorTick(World world, EntityPlayer player, ItemStack stack) {
        removeEnchantments(stack);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public ModelBiped getArmorModel(EntityLivingBase entity, ItemStack stack,
                                    EntityEquipmentSlot slot, ModelBiped defaultModel) {
        ModelBiped model = OtsutsukiArmorModels.modelFor(armorSet, slot);
        if (model == null) {
            return null;
        }
        model.setModelAttributes(defaultModel);
        return model;
    }

    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EntityEquipmentSlot slot, String type) {
        return armorSet.texture(slot == EntityEquipmentSlot.LEGS);
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        String key = getUnlocalizedName(stack) + ".name";
        String localized = I18n.translateToLocal(key);
        return key.equals(localized) ? displayName : localized;
    }

    private static void removeEnchantments(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            return;
        }
        tag.removeTag("ench");
        tag.removeTag("StoredEnchantments");
    }
}
