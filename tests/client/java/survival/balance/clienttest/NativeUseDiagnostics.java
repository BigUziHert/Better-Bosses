package survival.balance.clienttest;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.entity.player.Player;

/** Test-only observation. No method here mutates revival, item-use or player state. */
final class NativeUseDiagnostics {
    static Map<String,Object> observe(Player player){
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("usingItem",player.isUsingItem());result.put("useTicks",player.getTicksUsingItem());
        result.put("useRemaining",player.getUseItemRemainingTicks());result.put("passenger",player.isPassenger());result.put("vehicle",player.isVehicle());
        result.put("vehicleType",player.getVehicle()==null?"":net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(player.getVehicle().getType()).toString());
        try{result.put("managedUse",player.getClass().getMethod("ragrevival$isManagedUse").invoke(player));}
        catch(ReflectiveOperationException unavailable){result.put("managedUse","unavailable");}
        try{result.put("carrying",Class.forName("com.biguzi.ragrevival.compat.CarryOnCompat").getMethod("isCarrying",Player.class).invoke(null,player));}
        catch(ReflectiveOperationException unavailable){result.put("carrying","unavailable");}
        try{
            Class<?> client=Class.forName("com.biguzi.ragrevival.client.RevivalClient");
            for(String name:new String[]{"activeTarget","activeAction","activeHand","suppressUseUntilRelease"}){
                Field field=client.getDeclaredField(name);field.setAccessible(true);Object value=field.get(null);
                result.put(name,value==null?"":value.toString());
            }
        }catch(ReflectiveOperationException unavailable){result.put("revivalClient","unavailable");}
        return result;
    }
}
