package dev.genesi.amongus.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class RoleAssigner {

    private RoleAssigner() {
    }

    public static int impostorCount(int players, int playersPerImpostor, int maxImpostors) {
        if (players < 2) {
            return 0;
        }
        int per = Math.max(3, playersPerImpostor);
        int count = Math.max(1, players / per);
        return Math.min(Math.max(1, maxImpostors), Math.min(count, players - 1));
    }

    public static List<Boolean> assignMask(int players, int impostors) {
        List<Boolean> mask = new ArrayList<>(players);
        for (int i = 0; i < players; i++) {
            mask.add(i < impostors);
        }
        Collections.shuffle(mask, ThreadLocalRandom.current());
        return mask;
    }
}
