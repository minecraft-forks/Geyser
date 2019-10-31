package org.geysermc.connector.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nukkitx.nbt.NbtUtils;
import com.nukkitx.nbt.stream.NBTInputStream;
import com.nukkitx.nbt.tag.CompoundTag;
import com.nukkitx.nbt.tag.ListTag;
import com.nukkitx.protocol.bedrock.packet.StartGamePacket;

import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import org.geysermc.connector.GeyserConnector;
import org.geysermc.connector.console.GeyserLogger;
import org.geysermc.connector.network.translators.block.BlockEntry;
import org.geysermc.connector.network.translators.item.ItemEntry;
import org.geysermc.connector.world.GlobalBlockPalette;

import java.io.InputStream;
import java.util.*;

public class Toolbox {

    public static final Collection<StartGamePacket.ItemEntry> ITEMS;
    public static ListTag<CompoundTag> CACHED_PALLETE;

    public static final TIntObjectMap<ItemEntry> ITEM_ENTRIES;
    public static final TIntObjectMap<BlockEntry> BLOCK_ENTRIES;

    static {
        InputStream stream = GeyserConnector.class.getClassLoader().getResourceAsStream("bedrock/cached_palette.dat");
        if (stream == null) {
            throw new AssertionError("Unable to find cached_palette.dat");
        }

        Map<String, Integer> blockIdToIdentifier = new HashMap<>();
        CompoundTag tag;

        NBTInputStream nbtInputStream = NbtUtils.createNetworkReader(stream);
        try {
            tag = (CompoundTag) nbtInputStream.readTag();
            System.out.println(tag.getValue().values());
            System.out.println(tag.getAsList("Palette", CompoundTag.class));
            nbtInputStream.close();
        } catch (Exception ex) {
            GeyserLogger.DEFAULT.warning("Failed to get blocks from cached palette, please report this error!");
            throw new AssertionError(ex);
        }

        List<CompoundTag> entries = tag.getAsList("Palette", CompoundTag.class);
        for (CompoundTag entry : entries) {
            String name = entry.getAsString("name");
            int id = entry.getAsShort("id");
            int data = entry.getAsShort("meta");

            blockIdToIdentifier.put(name, id);
            GlobalBlockPalette.registerMapping(id << 4 | data);
        }

        CACHED_PALLETE = new ListTag<>("Palette", CompoundTag.class, tag.getAsList("Palette", CompoundTag.class));
        InputStream stream2 = Toolbox.class.getClassLoader().getResourceAsStream("bedrock/items.json");
        if (stream2 == null) {
            throw new AssertionError("Items Table not found");
        }

        ObjectMapper startGameItemMapper = new ObjectMapper();
        List<Map> startGameItems = new ArrayList<>();
        try {
            startGameItems = startGameItemMapper.readValue(stream2, ArrayList.class);
        } catch (Exception e) {
            e.printStackTrace();
        }

        List<StartGamePacket.ItemEntry> startGameEntries = new ArrayList<>();
        for (Map entry : startGameItems) {
            startGameEntries.add(new StartGamePacket.ItemEntry((String) entry.get("name"), (short) ((int) entry.get("id"))));
        }

        ITEMS = startGameEntries;

        InputStream itemStream = Toolbox.class.getClassLoader().getResourceAsStream("items.json");
        ObjectMapper itemMapper = new ObjectMapper();
        Map<String, Map<String, Object>> items = new HashMap<>();

        try {
            items = itemMapper.readValue(itemStream, LinkedHashMap.class);
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        TIntObjectMap<ItemEntry> itemEntries = new TIntObjectHashMap<>();
        int itemIndex = 0;

        for (Map.Entry<String, Map<String, Object>> itemEntry : items.entrySet()) {
            itemEntries.put(itemIndex, new ItemEntry(itemEntry.getKey(), itemIndex, (int) itemEntry.getValue().get("bedrock_id"), (int) itemEntry.getValue().get("bedrock_data")));
            itemIndex++;
        }

        ITEM_ENTRIES = itemEntries;

        InputStream blockStream = Toolbox.class.getClassLoader().getResourceAsStream("blocks.json");
        ObjectMapper blockMapper = new ObjectMapper();
        Map<String, Map<String, Object>> blocks = new HashMap<>();

        try {
            blocks = blockMapper.readValue(blockStream, LinkedHashMap.class);
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        TIntObjectMap<BlockEntry> blockEntries = new TIntObjectHashMap<>();
        int blockIndex = 0;

        for (Map.Entry<String, Map<String, Object>> itemEntry : blocks.entrySet()) {
            if (!blockIdToIdentifier.containsKey(itemEntry.getValue().get("bedrock_identifier"))) {
                GeyserLogger.DEFAULT.debug("Mapping " + itemEntry.getValue().get("bedrock_identifier") + " does not exist on bedrock edition!");
                blockEntries.put(blockIndex, new BlockEntry(itemEntry.getKey(), blockIndex, 248, 0)); // update block
            } else {
                blockEntries.put(blockIndex, new BlockEntry(itemEntry.getKey(), blockIndex, blockIdToIdentifier.get(itemEntry.getValue().get("bedrock_identifier")), (int) itemEntry.getValue().get("bedrock_data")));
            }

            blockIndex++;
        }

        BLOCK_ENTRIES = blockEntries;
    }
}