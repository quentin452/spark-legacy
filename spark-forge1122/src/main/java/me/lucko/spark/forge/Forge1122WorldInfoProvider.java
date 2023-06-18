/*
 * This file is part of spark.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package me.lucko.spark.forge;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import me.lucko.spark.common.platform.world.AbstractChunkInfo;
import me.lucko.spark.common.platform.world.CountMap;
import me.lucko.spark.common.platform.world.WorldInfoProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ClassInheritanceMultiMap;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.EntityRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public abstract class Forge1122WorldInfoProvider implements WorldInfoProvider {
    public static final class Server extends Forge1122WorldInfoProvider {
        private final MinecraftServer server;

        public Server(MinecraftServer server) {
            this.server = server;
        }

        @Override
        public ChunksResult<ForgeChunkInfo> pollChunks() {
            ChunksResult<ForgeChunkInfo> data = new ChunksResult<>();

            for (WorldServer level : this.server.worlds) {
                ArrayList<ForgeChunkInfo> list = new ArrayList<>();
                for(Chunk chunk : level.getChunkProvider().id2ChunkMap.values()) {
                    list.add(new ForgeChunkInfo(chunk));
                }
                data.put(level.provider.getDimensionType().getName(), list);
            }

            return data;
        }

        @Override
        public CountsResult pollCounts() {
            int players = this.server.getCurrentPlayerCount();
            int entities = 0;
            int chunks = 0;

            for (WorldServer level : this.server.worlds) {
                entities += level.loadedEntityList.size();
                chunks += level.getChunkProvider().getLoadedChunkCount();
            }

            return new CountsResult(players, entities, -1, chunks);
        }
    }

    @SideOnly(Side.CLIENT)
    public static final class Client extends Forge1122WorldInfoProvider {
        private final Minecraft client;

        public Client(Minecraft client) {
            this.client = client;
        }

        @Override
        public ChunksResult<ForgeChunkInfo> pollChunks() {
            ChunksResult<ForgeChunkInfo> data = new ChunksResult<>();

            WorldClient level = this.client.world;
            if (level == null) {
                return null;
            }

            ArrayList<ForgeChunkInfo> list = new ArrayList<>();
            IChunkProvider provider = level.getChunkProvider();
            if(provider instanceof ChunkProviderClient) {
                List<Chunk> chunks = ObfuscationReflectionHelper.getPrivateValue(ChunkProviderClient.class, (ChunkProviderClient)provider, "field_73237_c");
                for(Chunk chunk : chunks) {
                    list.add(new ForgeChunkInfo(chunk));
                }
            }

            data.put(level.provider.getDimensionType().getName(), list);

            return data;
        }

        @Override
        public CountsResult pollCounts() {
            WorldClient level = this.client.world;
            if (level == null) {
                return null;
            }

            Long2ObjectMap<Chunk> mapping = ObfuscationReflectionHelper.getPrivateValue(ChunkProviderClient.class, level.getChunkProvider(), "field_73236_b");

            return new CountsResult(-1, level.loadedEntityList.size(), -1, mapping.size());
        }
    }

    static final class ForgeChunkInfo extends AbstractChunkInfo<Class<? extends Entity>> {
        private final CountMap<Class<? extends Entity>> entityCounts;

        ForgeChunkInfo(Chunk chunk) {
            super(chunk.x, chunk.z);

            this.entityCounts = new CountMap.Simple<>(new HashMap<>());
            for(ClassInheritanceMultiMap<Entity> entityList : chunk.getEntityLists()) {
                entityList.forEach(entity -> {
                    this.entityCounts.increment(entity.getClass());
                });
            }
        }

        @Override
        public CountMap<Class<? extends Entity>> getEntityCounts() {
            return this.entityCounts;
        }

        @Override
        public String entityTypeName(Class<? extends Entity> type) {
            EntityEntry entry = EntityRegistry.getEntry(type);
            return entry == null ? "unknown" : entry.getName();
        }
    }


}
