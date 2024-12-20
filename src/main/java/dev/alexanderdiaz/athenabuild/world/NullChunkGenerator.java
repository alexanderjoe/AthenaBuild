package dev.alexanderdiaz.athenabuild.world;

import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;

import java.util.Random;

@SuppressWarnings("deprecation")
public class NullChunkGenerator extends ChunkGenerator {
    @Override
    public byte[] generate(World world, Random random, int x, int z) {
        return new byte[65536];
    }
}
