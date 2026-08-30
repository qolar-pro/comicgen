package pro.qolar.walls.loot;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * What the centre chests are stocked with.
 *
 * <p>Deliberately plain items. Enchantments and potion types were both reworked
 * between 1.20 and 1.21, so putting them in loot is the fastest way to make one
 * jar stop working across versions. Everything here has been stable since 1.13.
 */
public final class LootTable {

    /** One possible chest item: a material, a stack size range, and a weight. */
    private record Entry(Material material, int min, int max, int weight) {
    }

    private static final List<Entry> ENTRIES = List.of(
            // Blocks - the bread and butter of walling yourself in.
            new Entry(Material.OAK_PLANKS, 8, 24, 10),
            new Entry(Material.COBBLESTONE, 8, 32, 10),
            new Entry(Material.OAK_LOG, 4, 12, 6),
            new Entry(Material.LADDER, 4, 12, 4),
            new Entry(Material.TORCH, 4, 12, 4),

            // Food.
            new Entry(Material.BREAD, 3, 8, 9),
            new Entry(Material.COOKED_BEEF, 2, 6, 7),
            new Entry(Material.GOLDEN_APPLE, 1, 1, 2),

            // Materials.
            new Entry(Material.IRON_INGOT, 2, 6, 8),
            new Entry(Material.GOLD_INGOT, 1, 4, 5),
            new Entry(Material.DIAMOND, 1, 2, 2),

            // Weapons and tools.
            new Entry(Material.STONE_SWORD, 1, 1, 6),
            new Entry(Material.IRON_SWORD, 1, 1, 3),
            new Entry(Material.IRON_PICKAXE, 1, 1, 4),
            new Entry(Material.IRON_AXE, 1, 1, 3),
            new Entry(Material.BOW, 1, 1, 4),
            new Entry(Material.ARROW, 8, 20, 6),
            new Entry(Material.SHIELD, 1, 1, 3),

            // Armour.
            new Entry(Material.IRON_HELMET, 1, 1, 3),
            new Entry(Material.IRON_CHESTPLATE, 1, 1, 2),
            new Entry(Material.IRON_LEGGINGS, 1, 1, 2),
            new Entry(Material.IRON_BOOTS, 1, 1, 3),
            new Entry(Material.CHAINMAIL_CHESTPLATE, 1, 1, 3),

            // Tricks.
            new Entry(Material.ENDER_PEARL, 1, 2, 3),
            new Entry(Material.TNT, 1, 3, 2),
            new Entry(Material.WATER_BUCKET, 1, 1, 3));

    private static final int TOTAL_WEIGHT = ENTRIES.stream().mapToInt(Entry::weight).sum();

    private LootTable() {
    }

    /**
     * Stock an inventory with {@code stacks} randomly chosen items, scattered
     * across random slots.
     */
    public static void fill(Inventory inventory, Random random, int stacks) {
        inventory.clear();
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < inventory.getSize(); i++) {
            slots.add(i);
        }
        Collections.shuffle(slots, random);

        int placed = 0;
        for (int slot : slots) {
            if (placed >= stacks) {
                break;
            }
            inventory.setItem(slot, roll(random));
            placed++;
        }
    }

    private static ItemStack roll(Random random) {
        int pick = random.nextInt(TOTAL_WEIGHT);
        for (Entry entry : ENTRIES) {
            pick -= entry.weight();
            if (pick < 0) {
                int amount = entry.min() + random.nextInt(entry.max() - entry.min() + 1);
                return new ItemStack(entry.material(), amount);
            }
        }
        return new ItemStack(Material.BREAD, 1);
    }
}
