package survival.balance.tests;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/** Native collision callbacks retain their own impact/explosion order and hurt cooldown. */
final class CompoundProjectileCases {
    private static final String UR="twilightforest:ur_ghast", HARB="cataclysm:the_harbinger";
    private static boolean stressListenerInstalled;
    private static BossBalanceTests.TestPlayer noIframeVictim;
    private static int cooldownClears;
    static void run(ServerLevel level,Path output) throws Exception {
        if(!stressListenerInstalled){NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,CompoundProjectileCases::beforeStressExplosion);stressListenerInstalled=true;}
        ExhaustiveCapture.reset("");
        var victim=BossBalanceTests.damagePlayer(level);
        var bystander=BossBalanceTests.damagePlayer(level);
        for(boolean netherite:List.of(false,true))for(String kind:List.of("ur_fireball","harb_missile","harb_homing")) {
            for(String scenario:List.of("direct_plus_splash","splash_only","raised_shield","active_iframes","clean_miss","native_reflection_attempt","two_victims","second_projectile"))
                scenario(level,victim,bystander,kind,scenario,netherite);
        }
        for(boolean netherite:List.of(false,true))for(String scenario:List.of("direct_plus_splash","splash_only","two_victims","second_projectile"))scenario(level,victim,bystander,"ur_fireball_power4",scenario,netherite);
        for(boolean netherite:List.of(false,true)) {
            for(String kind:List.of("ur_fireball","harb_missile","harb_homing"))exhaustion(level,victim,kind,netherite,false);
            for(String kind:List.of("ur_fireball_power4","harb_missile","harb_homing"))exhaustion(level,victim,kind,netherite,true);
        }
        ExhaustiveCapture.note("all","scope","methodology","Native installed projectile constructors and complete onHit callbacks; Hard, 20 HP, plain diamond and full Protection IV netherite. Direct impact and explosion share health and invulnerability frames. Flight-away miss uses real projectile ticks. Shield and reflection use native player/projectile methods. Distinct-projectile scenario clears hurt cooldown between separate collisions, but does not reset the first projectile's ledger. No synthetic explosion or replacement damage source is supplied. Ur power4 is an explicitly stronger native-constructor stress variant, not a claim that the installed boss ordinarily shoots power4. Read-only production ledger diagnostics prove per-projectile/per-victim accounting; actual accepted damage remains independently captured.");
        ExhaustiveCapture.note("all","adversarial_budget_scope","methodology","Explicit compatibility stress, separate from ordinary gameplay: the same held projectile object receives repeated native onHit callbacks even after native discard, with only the measuring victim's hurt cooldown cleared between callbacks. Health, armor items/durability, effects and the projectile ledger are retained until all eight reference hearts are spent, followed by another callback that must do no damage. In simulated_no_iframes cases only, a test listener also clears the fixture victim's hurt cooldown at native ExplosionEvent.Start to model a mod that removes same-hit invulnerability. Native explosions, source attribution, damage processing and projectile ownership remain unchanged. Ur power4 then exercises a single accepted4.5+3.5 budget split. These adversarial repeated callbacks and no-iFrame conditions are not claimed to occur in ordinary play.");
        ExhaustiveCapture.write(output);
    }
    private static void beforeStressExplosion(ExplosionEvent.Start event) {
        if(noIframeVictim==null||event.getLevel()!=noIframeVictim.level())return;
        try{clearCooldown(noIframeVictim);cooldownClears++;}
        catch(Exception error){throw new IllegalStateException("Cannot clear only the stress fixture's hurt cooldown",error);}
    }
    private static void exhaustion(ServerLevel level,BossBalanceTests.TestPlayer player,String kind,boolean netherite,boolean noIframes) {
        String id=kind.startsWith("ur_")?UR:HARB;
        String name=(netherite?"netherite_p4/":"diamond/")+kind+"/adversarial_"+(noIframes?"simulated_no_iframes":"repeated_native_callbacks");
        LivingEntity boss=null;Projectile shot=null;
        try {
            reset(player,netherite); // The only health/equipment reset in this whole scenario.
            boss=BossBalanceTests.damageBoss(level,id);boss.moveTo(8,250,18,180,0);
            shot=projectile(level,boss,player,kind);
            double startingHp=player.getHealth(),observedCost=0;int callbacks=0;boolean truncated=false;
            double nominalImpact=kind.startsWith("ur_")?4.5:2.25;
            for(;callbacks<6&&spent(shot,player)<8-.0005;callbacks++) {
                double previousBudget=spent(shot,player),remaining=8-previousBudget;
                clearCooldown(player);shot.moveTo(8,250,8.5,180,0);
                cooldownClears=0;noIframeVictim=noIframes?player:null;
                ExhaustiveCapture.begin(id,name+"/callback_"+(callbacks+1),player);
                try{invoke(shot,"onHit",new Class<?>[]{HitResult.class},new EntityHitResult(player));}
                finally{noIframeVictim=null;}
                var hits=ExhaustiveCapture.end();
                check(id,name+"_callback_"+(callbacks+1)+"_native_damage",!hits.isEmpty());
                double thisCost=referenceCost(hits,netherite);observedCost+=thisCost;
                double ledger=spent(shot,player);
                check(id,name+"_callback_"+(callbacks+1)+"_never_overspends",ledger<=8.0005&&observedCost<=8.0005);
                BossBalanceTests.result("compound_"+name+"_callback_"+(callbacks+1)+"_accepted_cost",id,thisCost,ledger-previousBudget);
                if(remaining<nominalImpact-.001&&!hits.isEmpty()) {
                    double expected=netherite?BalanceExpectations.netheriteProtected(remaining):remaining;
                    BossBalanceTests.result("compound_"+name+"_remaining_impact_is_truncated",id,expected,hits.getFirst().damageHp()/2);
                    truncated=true;
                }
                if(noIframes) {
                    check(id,name+"_callback_"+(callbacks+1)+"_native_explosion_hook",cooldownClears==1);
                    double predicted=0,available=remaining;
                    for(var hit:hits) {
                        boolean explosion=hit.type().contains("explosion");
                        double nativeDiamond=hit.originalIncomingHp()==null?Double.NaN:BalanceExpectations.diamondHearts(hit.originalIncomingHp());
                        double withoutJointLimit=explosion?Math.min(nativeDiamond,kind.startsWith("ur_")?6:6.75):nominalImpact;
                        double cost=Math.min(available,withoutJointLimit);
                        if(cost<withoutJointLimit-.001)truncated=true;
                        predicted+=netherite?BalanceExpectations.netheriteProtected(cost):cost;available-=cost;
                    }
                    BossBalanceTests.result("compound_"+name+"_callback_"+(callbacks+1)+"_independent_joint_damage",id,predicted,hits.stream().mapToDouble(h->h.damageHp()/2).sum());
                    if(callbacks==0) {
                        check(id,name+"_first_collision_accepts_impact_and_splash",hits.size()==2);
                        if(kind.endsWith("power4")) {
                            BossBalanceTests.result("compound_"+name+"_first_collision_spends_eight",id,8,ledger);
                        }
                    }
                }
                ExhaustiveCapture.note(id,name+"/callback_"+(callbacks+1)+"_budget","measurement","Accepted reference cost="+thisCost+"; projectile ledger="+ledger+"; cumulative independent reference cost="+observedCost+"; unchanged-gear health="+player.getHealth()+". Cooldown cleared at native explosion="+noIframes+".");
            }
            BossBalanceTests.result("compound_"+name+"_exhausted_budget",id,8,spent(shot,player));
            BossBalanceTests.result("compound_"+name+"_independent_reference_total",id,8,observedCost);
            check(id,name+"_remaining_budget_was_truncated",truncated);
            if(!netherite)BossBalanceTests.result("compound_"+name+"_retained_health_loses_eight",id,8,(startingHp-player.getHealth())/2);
            double healthBefore=player.getHealth(),budgetBefore=spent(shot,player);
            clearCooldown(player);shot.moveTo(8,250,8.5,180,0);noIframeVictim=noIframes?player:null;
            ExhaustiveCapture.begin(id,name+"/callback_after_exhaustion",player);
            try{invoke(shot,"onHit",new Class<?>[]{HitResult.class},new EntityHitResult(player));}
            finally{noIframeVictim=null;}
            var rejected=ExhaustiveCapture.end();
            BossBalanceTests.result("compound_"+name+"_exhaustion_blocks_health_loss",id,healthBefore,player.getHealth());
            BossBalanceTests.result("compound_"+name+"_exhaustion_cannot_spend_again",id,budgetBefore,spent(shot,player));
            // Native floats can retain a sub-microheart remainder after the last split.
            check(id,name+"_exhaustion_no_material_accepted_damage",rejected.stream().mapToDouble(h->h.damageHp()).sum()<=.00001&&Math.abs(healthBefore-player.getHealth())<=.00001&&Math.abs(budgetBefore-spent(shot,player))<=.00001);
        }catch(Throwable failure){BossBalanceTests.error("compound_"+name,id,failure);}
        finally{noIframeVictim=null;ExhaustiveCapture.end();player.clearFire();if(shot!=null)shot.discard();if(boss!=null)boss.discard();}
    }
    private static void clearCooldown(BossBalanceTests.TestPlayer player)throws Exception{player.invulnerableTime=0;field(player,"lastHurt",0f);}
    /** Independently undo actual netherite+Protection mitigation for each accepted hit. */
    private static double referenceCost(List<ExhaustiveCapture.Hit> hits,boolean netherite) {
        double cost=0;
        for(var hit:hits) {
            if(!netherite){cost+=hit.damageHp()/2;continue;}
            double low=0,high=1000;
            for(int i=0;i<80;i++){
                double raw=(low+high)/2;
                double received=raw*(1-Math.max(4,Math.min(20,20-raw/5))/25)*.36;
                if(received<hit.damageHp())low=raw;else high=raw;
            }
            cost+=BalanceExpectations.diamondHearts((low+high)/2);
        }
        return cost;
    }
    private static void scenario(ServerLevel level,BossBalanceTests.TestPlayer player,BossBalanceTests.TestPlayer other,String kind,String name,boolean netherite) {
        String caseId=(netherite?"netherite_p4/":"diamond/")+kind;
        String id=kind.startsWith("ur_")?UR:HARB;LivingEntity boss=null;Projectile projectile=null,second=null;
        try {
            reset(player,netherite);reset(other,netherite);other.moveTo(108,250,108,0,0);
            boss=BossBalanceTests.damageBoss(level,id);boss.moveTo(8,250,18,180,0);
            projectile=projectile(level,boss,player,kind);projectile.moveTo(8,250,8.5,180,0);
            double initial=player.getHealth();
            if(name.equals("raised_shield")) {
                player.setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(Items.SHIELD));player.startUsingItem(InteractionHand.OFF_HAND);
                field(player,"useItemRemaining",71990);check(id,caseId+"_shield_is_raised",player.isBlocking());
            }
            if(name.equals("active_iframes")){player.invulnerableTime=20;field(player,"lastHurt",1000f);}
            if(name.equals("two_victims"))other.moveTo(player.position());
            if(name.equals("native_reflection_attempt")) {
                // In Minecraft 1.21 the native redirectable-projectile check lives
                // in Player.attack; AbstractHurtingProjectile.hurt only accepts damage.
                other.moveTo(8,250,9,180,0);other.attack(projectile);other.moveTo(108,250,108,0,0);
                boolean reflected=projectile.getOwner()==other;
                check(id,caseId+"_native_reflection_semantics",reflected==kind.startsWith("ur_"));
            }
            Map<?,?> beforeProfiles=profiles();
            ExhaustiveCapture.begin(id,caseId+"/"+name,player);
            if(name.equals("clean_miss")) {
                projectile.moveTo(8,270,8,0,0);projectile.setDeltaMovement(0,1,0);
                // Homing missiles would naturally steer back toward their target: retain the
                // native constructor but observe only the initial distant flight segment.
                for(int tick=0;tick<3&&!projectile.isRemoved();tick++){projectile.tickCount++;projectile.tick();}
            } else if(name.equals("splash_only")) {
                projectile.moveTo(player.position());
                invoke(projectile,"onHit",new Class<?>[]{HitResult.class},new BlockHitResult(projectile.position(),Direction.UP,projectile.blockPosition(),false));
            } else {
                invoke(projectile,"onHit",new Class<?>[]{HitResult.class},new EntityHitResult(player));
            }
            List<ExhaustiveCapture.Hit> hits=ExhaustiveCapture.end();
            double lost=(initial-player.getHealth())/2;
            boolean reflected=name.equals("native_reflection_attempt")&&kind.startsWith("ur_");
            if(!reflected)check(id,caseId+"_"+name+"_joint_eight_cap",lost<=(netherite?2.592:8)+.005);
            if(name.equals("clean_miss")||name.equals("active_iframes")) {
                BossBalanceTests.result("compound_"+caseId+"_"+name+"_health_unchanged",id,0,lost);
                BossBalanceTests.result("compound_"+caseId+"_"+name+"_budget_unspent",id,0,spent(projectile,player));
            } else if(reflected) {
                check(id,caseId+"_reflected_profile_untouched",beforeProfiles.equals(profiles()));
                BossBalanceTests.result("compound_"+caseId+"_reflected_budget_unspent",id,0,spent(projectile,player));
                check(id,caseId+"_reflected_native_damage_observed",!hits.isEmpty());
            } else {
                if(!name.equals("raised_shield"))check(id,caseId+"_"+name+"_native_damage_observed",!hits.isEmpty());
                double budget=spent(projectile,player);check(id,caseId+"_"+name+"_ledger_valid",Double.isFinite(budget)&&budget>=0&&budget<=8.005);
                if(name.equals("raised_shield")) {
                    if(hits.isEmpty())BossBalanceTests.result("compound_"+caseId+"_fully_blocked_budget_unspent",id,0,budget);
                    check(id,caseId+"_shield_blocks_direct",hits.stream().noneMatch(h->h.type().equals("minecraft:fireball")||h.type().equals("minecraft:mob_projectile")));
                    check(id,caseId+"_shield_does_not_reserve_impact",budget<=(kind.startsWith("ur_")?6:6.75)+.005);
                }
                if(name.equals("splash_only")){double cap=kind.startsWith("ur_")?6:6.75;check(id,caseId+"_splash_only_ceiling",lost<=(netherite?BalanceExpectations.netheriteProtected(cap):cap)+.005);}
            }
            if(name.equals("two_victims")) {
                double bystanderLost=(20-other.getHealth())/2;
                double bystanderCap=kind.startsWith("ur_")?6:6.75;
                check(id,caseId+"_bystander_native_splash",bystanderLost>0&&bystanderLost<=(netherite?BalanceExpectations.netheriteProtected(bystanderCap):bystanderCap)+.005);
                check(id,caseId+"_bystander_separate_budget",spent(projectile,other)>0&&spent(projectile,other)<=8.005);
                ExhaustiveCapture.note(id,caseId+"/bystander","measurement","Same projectile explosion, second 20-HP victim in matching gear: "+bystanderLost+" hearts; spent reference budget="+spent(projectile,other)+". No shared server/player-count budget.");
            }
            if(name.equals("second_projectile")) {
                double firstSpent=spent(projectile,player);
                reset(player,netherite);second=projectile(level,boss,player,kind);second.moveTo(8,250,8.5,180,0);
                BossBalanceTests.result("compound_"+caseId+"_new_projectile_initial_budget",id,0,spent(second,player));
                ExhaustiveCapture.begin(id,caseId+"/second_independent_collision",player);
                invoke(second,"onHit",new Class<?>[]{HitResult.class},new EntityHitResult(player));
                var secondHits=ExhaustiveCapture.end();
                check(id,caseId+"_second_projectile_damage",!secondHits.isEmpty());
                BossBalanceTests.result("compound_"+caseId+"_same_native_outcome_per_projectile",id,lost,(20-player.getHealth())/2);
                BossBalanceTests.result("compound_"+caseId+"_first_budget_unchanged",id,firstSpent,spent(projectile,player));
            }
        }catch(Throwable failure){BossBalanceTests.error("compound_"+caseId+"_"+name,id,failure);}
        finally{ExhaustiveCapture.end();player.stopUsingItem();player.clearFire();other.clearFire();if(projectile!=null)projectile.discard();if(second!=null)second.discard();if(boss!=null)boss.discard();}
    }
    private static void reset(BossBalanceTests.TestPlayer player,boolean netherite)throws Exception{
        BossBalanceTests.resetActualPlayer(player);player.moveTo(8,250,8,0,0);field(player,"lastHurt",0f);
        if(netherite){
            var slots=List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET);
            var items=List.of(Items.NETHERITE_HELMET,Items.NETHERITE_CHESTPLATE,Items.NETHERITE_LEGGINGS,Items.NETHERITE_BOOTS);
            var protection=player.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.PROTECTION);
            for(int i=0;i<slots.size();i++){ItemStack stack=new ItemStack(items.get(i));stack.enchant(protection,4);player.setItemSlot(slots.get(i),stack);}
            BossBalanceTests.updateEquipment(player);
        }
    }
    private static Projectile projectile(ServerLevel level,LivingEntity boss,BossBalanceTests.TestPlayer target,String kind)throws Exception {
        if(kind.startsWith("ur_"))return (Projectile)Class.forName("twilightforest.entity.projectile.UrGhastFireball")
            .getConstructor(Level.class,boss.getClass(),double.class,double.class,double.class,int.class).newInstance(level,boss,0d,0d,-1d,kind.endsWith("power4")?4:1);
        float damage=((Number)Class.forName("com.github.L_Ender.cataclysm.config.CMCommonConfig$Harbinger").getField("WitherMissiledamage").get(null)).floatValue();
        if(kind.equals("harb_homing"))return (Projectile)Class.forName("com.github.L_Ender.cataclysm.entity.projectile.Wither_Homing_Missile_Entity")
            .getConstructor(LivingEntity.class,Vec3.class,Level.class,float.class,LivingEntity.class).newInstance(boss,new Vec3(0,0,-1),level,damage,target);
        return (Projectile)Class.forName("com.github.L_Ender.cataclysm.entity.projectile.Wither_Missile_Entity")
            .getConstructor(LivingEntity.class,Vec3.class,Level.class,float.class).newInstance(boss,new Vec3(0,0,-1),level,damage);
    }
    private static double spent(Entity projectile,Player victim)throws Exception{
        return ((Number)Class.forName("survival.balance.BossDamageBalance").getMethod("combinedBudgetUsed",Entity.class,Player.class).invoke(null,projectile,victim)).doubleValue();
    }
    private static Map<?,?> profiles()throws Exception{return (Map<?,?>)Class.forName("survival.balance.BossDamageBalance").getMethod("profileCounts").invoke(null);}
    private static void check(String id,String name,boolean pass){BossBalanceTests.result("compound_"+name,id,1,pass?1:0);}
    private static void invoke(Object target,String name,Class<?>[] signature,Object...args)throws Exception{
        for(Class<?> type=target.getClass();type!=null;type=type.getSuperclass())try{Method m=type.getDeclaredMethod(name,signature);m.setAccessible(true);m.invoke(target,args);return;}catch(NoSuchMethodException ignored){}
        throw new NoSuchMethodException(name);
    }
    private static void field(Object target,String name,Object value)throws Exception{
        for(Class<?> type=target.getClass();type!=null;type=type.getSuperclass())try{Field f=type.getDeclaredField(name);f.setAccessible(true);f.set(target,value);return;}catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(name);
    }
}
