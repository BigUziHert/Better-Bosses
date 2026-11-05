package survival.balance.tests;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

/** Starting equipment for the continuous survival runs. Never replenishes during combat. */
final class FightLoadouts {
    private FightLoadouts() {}

    static void maxNetherite(ServerPlayer player) {
        var enchantments = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        ItemStack helmet = armor(enchantments, Items.NETHERITE_HELMET);
        enchant(enchantments, helmet, Enchantments.RESPIRATION, 3);
        enchant(enchantments, helmet, Enchantments.AQUA_AFFINITY, 1);
        player.setItemSlot(EquipmentSlot.HEAD, helmet);
        player.setItemSlot(EquipmentSlot.CHEST, armor(enchantments, Items.NETHERITE_CHESTPLATE));
        ItemStack leggings = armor(enchantments, Items.NETHERITE_LEGGINGS);
        enchant(enchantments, leggings, Enchantments.SWIFT_SNEAK, 3);
        player.setItemSlot(EquipmentSlot.LEGS, leggings);
        ItemStack boots = armor(enchantments, Items.NETHERITE_BOOTS);
        enchant(enchantments, boots, Enchantments.FEATHER_FALLING, 4);
        enchant(enchantments, boots, Enchantments.DEPTH_STRIDER, 3);
        enchant(enchantments, boots, Enchantments.SOUL_SPEED, 3);
        player.setItemSlot(EquipmentSlot.FEET, boots);

        ItemStack sword = durable(enchantments, Items.NETHERITE_SWORD);
        enchant(enchantments, sword, Enchantments.SHARPNESS, 5);
        enchant(enchantments, sword, Enchantments.SWEEPING_EDGE, 3);
        enchant(enchantments, sword, Enchantments.LOOTING, 3);
        enchant(enchantments, sword, Enchantments.FIRE_ASPECT, 2);
        enchant(enchantments, sword, Enchantments.KNOCKBACK, 2);
        ItemStack bow = durable(enchantments, Items.BOW);
        enchant(enchantments, bow, Enchantments.POWER, 5);
        enchant(enchantments, bow, Enchantments.FLAME, 1);
        ItemStack pickaxe = durable(enchantments, Items.NETHERITE_PICKAXE);
        enchant(enchantments, pickaxe, Enchantments.EFFICIENCY, 5);
        var inventory = player.getInventory();
        inventory.setItem(0, sword);
        inventory.setItem(1, bow);
        inventory.setItem(2, new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 64));
        inventory.setItem(3, pickaxe);
        for (int slot = 4; slot < 8; slot++) {
            inventory.setItem(slot, new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 64));
        }
        // Slot 8 is intentionally empty for reflecting Lich bolts without sword interception.
        for (int slot = 9; slot < 25; slot++) {
            inventory.setItem(slot, new ItemStack(Items.ARROW, 64));
        }
        player.setItemInHand(InteractionHand.OFF_HAND, durable(enchantments, Items.SHIELD));
        inventory.selected = 0;
    }

    private static ItemStack armor(HolderLookup<Enchantment> enchantments, Item item) {
        ItemStack stack = durable(enchantments, item);
        enchant(enchantments, stack, Enchantments.PROTECTION, 4);
        enchant(enchantments, stack, Enchantments.THORNS, 3);
        return stack;
    }

    private static ItemStack durable(HolderLookup<Enchantment> enchantments, Item item) {
        ItemStack stack = new ItemStack(item);
        enchant(enchantments, stack, Enchantments.UNBREAKING, 3);
        enchant(enchantments, stack, Enchantments.MENDING, 1);
        return stack;
    }

    private static void enchant(HolderLookup<Enchantment> enchantments, ItemStack stack,
                                ResourceKey<Enchantment> key, int level) {
        stack.enchant(enchantments.getOrThrow(key), level);
    }
}
