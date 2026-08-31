package stonytark.cinemarr.core.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Process-wide required-client hello ownership shared by non-Fabric adapters. */
public final class RequiredClientGate {
    private static final long TIMEOUT_MS = 5_000L;
    private static final Map<UUID,Long> pending = new ConcurrentHashMap<UUID,Long>();
    private static final Map<UUID,Boolean> accepted = new ConcurrentHashMap<UUID,Boolean>();

    public static void require(UUID player,long nowMs){if(player==null)throw new IllegalArgumentException("player");accepted.remove(player);pending.put(player,saturatedAdd(nowMs,TIMEOUT_MS));}
    public static boolean accept(UUID player){if(player==null||pending.remove(player)==null)return false;return accepted.putIfAbsent(player,Boolean.TRUE)==null;}
    public static boolean accepted(UUID player){return player!=null&&accepted.containsKey(player);}
    public static void remove(UUID player){if(player==null)return;pending.remove(player);accepted.remove(player);}
    public static List<UUID> expire(long nowMs){List<UUID> values=new ArrayList<UUID>();for(Map.Entry<UUID,Long> entry:pending.entrySet())if(nowMs>=entry.getValue()&&pending.remove(entry.getKey(),entry.getValue()))values.add(entry.getKey());return values;}
    public static void clear(){pending.clear();accepted.clear();}
    private static long saturatedAdd(long left,long right){return left>Long.MAX_VALUE-right?Long.MAX_VALUE:left+right;}
    private RequiredClientGate(){}
}
