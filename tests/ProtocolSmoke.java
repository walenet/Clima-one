import com.climaone.app.core.AdvancedMidea;
import com.climaone.app.core.MideaLan;
import com.climaone.app.core.SafeEco;
import java.util.Map;

/** Pure-Java regression tests. No device contacted or credentials used. */
public final class ProtocolSmoke {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        byte[] breezeless=AdvancedMidea.b0Set(AdvancedMidea.BREEZELESS,1);
        check((breezeless[9]&255)==2,"B0 must be a set command");
        check((breezeless[10]&255)==0xb0,"B0 body type");
        check((breezeless[11]&255)==1,"one vendor tag");
        check((breezeless[12]&255)==0x18,"breeze property 0x0018");
        check((breezeless[14]&255)==1 && (breezeless[15]&255)==1,"value 1");

        byte[] quiet=AdvancedMidea.b0Set(AdvancedMidea.OUTDOOR_SILENT,3);
        check((quiet[12]&255)==0xcd && (quiet[15]&255)==3,"external silent value 3");
        byte[] gear=AdvancedMidea.b0Set(AdvancedMidea.GEAR,50);
        check((gear[12]&255)==0x48 && (gear[15]&255)==50,"manufacturer GEAR=50");
        try {
            AdvancedMidea.b0Set(AdvancedMidea.GEAR,150);
            throw new AssertionError("GEAR=150 must be rejected");
        }catch(IllegalArgumentException expected){}
        try {
            AdvancedMidea.b0Set(0x1122,1);
            throw new AssertionError("unknown B0 tag must be rejected");
        }catch(IllegalArgumentException expected){}

        byte[] b1=AdvancedMidea.b1Query(AdvancedMidea.OUTDOOR_SILENT);
        check((b1[10]&255)==0xb1 && (b1[12]&255)==0xcd,"B1 query");
        byte[] b1reply=MideaLan.frame(3,MideaLan.hex("b101cd00000103"));
        Map<Integer,byte[]> b1tags=AdvancedMidea.decodeTags(b1reply,0xb1);
        check(b1tags.containsKey(AdvancedMidea.OUTDOOR_SILENT),"B1 tag parsed");
        check((b1tags.get(AdvancedMidea.OUTDOOR_SILENT)[0]&255)==3,"B1 value parsed");

        byte[] b5reply=MideaLan.frame(3,MideaLan.hex("b50148000101"));
        Map<Integer,byte[]> b5tags=AdvancedMidea.decodeTags(b5reply,0xb5);
        check(b5tags.containsKey(AdvancedMidea.GEAR),"B5 capability parsed");
        check((b5tags.get(AdvancedMidea.GEAR)[0]&255)==1,"B5 rate levels flags");
        check(AdvancedMidea.b5Query(false)[10]==(byte)0xb5,"B5 first query");

        MideaLan.State cold=new MideaLan.State();
        cold.power=true;cold.mode=2;cold.setpoint=22;cold.turbo=true;
        MideaLan.State coldEco=SafeEco.superEco(cold);
        check(coldEco.setpoint==25 && coldEco.fan==20 && coldEco.eco
            && !coldEco.turbo,"Cool SUPER ECO must only use normal commands");
        MideaLan.State heat=new MideaLan.State();
        heat.power=true;heat.mode=4;heat.eco=true;heat.setpoint=23;heat.turbo=true;
        MideaLan.State heatEco=SafeEco.superEco(heat);
        check(heatEco.setpoint==20 && heatEco.fan==20 && !heatEco.eco &&
              !heatEco.turbo,"Heat SUPER ECO avoids COOL-only ECO");
        try{
            coldEco.fan=150;
            MideaLan.setState(coldEco);
            throw new AssertionError("150 percent fan request must be rejected");
        }catch(IllegalArgumentException expected){}
        System.out.println("PASS: B5/B1/B0 vendor wire format, safe control limits, SUPER ECO");
    }
}
