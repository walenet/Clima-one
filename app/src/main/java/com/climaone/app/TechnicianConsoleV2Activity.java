package com.climaone.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.*;
import com.climaone.app.core.AdvancedMidea;
import com.climaone.app.core.MideaLan;
import com.climaone.app.core.PrivateKeys;
import com.climaone.app.core.SafeEco;
import com.climaone.app.core.TechnicianReport;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Direct LAN professional console. All writes are documented standard Midea
 * controls or allowlisted B0 properties, never arbitrary inverter registers.
 * Authentication is shared with the LAB's main screen, not sent to this class.
 */
public final class TechnicianConsoleV2Activity extends Activity {
    private static final int BG=Color.rgb(9,23,36), PANEL=Color.rgb(22,45,63);
    private static final int WHITE=Color.WHITE, TEXT=Color.rgb(174,202,219);
    private static final int BLUE=Color.rgb(95,221,219), AMBER=Color.rgb(252,194,106);
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private LinearLayout root,deviceList,livePanel,commandPanel,featurePanel;
    private TextView feedback;
    private final ArrayList<MideaLan.Device> devices=new ArrayList<>();
    private MideaLan.Device connected;
    private MideaLan.State state;
    private AdvancedMidea.Snapshot caps;
    private long readAt;
    private volatile boolean working;

    @Override public void onCreate(Bundle data) {
        super.onCreate(data);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        draw();
    }
    private int dp(int x){return (int)(x*getResources().getDisplayMetrics().density);}
    private GradientDrawable rounded(int color,int radius) {
        GradientDrawable d=new GradientDrawable();d.setColor(color);
        d.setCornerRadius(dp(radius));d.setStroke(dp(1),Color.rgb(49,82,102));
        return d;
    }
    private LinearLayout vertical(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
    private TextView text(String value,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        t.setPadding(0,dp(3),0,dp(4));return t;
    }
    private void gap(LinearLayout p,int h){p.addView(new View(this),new LinearLayout.LayoutParams(1,dp(h)));}
    private LinearLayout panel(LinearLayout parent,String title){
        LinearLayout l=vertical();l.setPadding(dp(16),dp(14),dp(16),dp(14));
        l.setBackground(rounded(PANEL,20));l.setElevation(dp(2));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin=dp(12);parent.addView(l,lp);
        l.addView(text(title,13,BLUE,true));gap(l,7);return l;
    }
    private Button action(LinearLayout parent,String name,boolean primary,Runnable task){
        Button b=new Button(this);
        b.setAllCaps(false);b.setText(name);b.setGravity(Gravity.CENTER);
        b.setTextSize(15);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setTextColor(primary?BG:WHITE);
        int tint=primary?BLUE:Color.rgb(45,76,98);
        b.setBackground(new RippleDrawable(
            ColorStateList.valueOf(primary?0x44001B28:0x55FFFFFF),
            rounded(tint,16),null));
        b.setOnClickListener(v->{
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            if(working){status("Attendi la fine del comando in corso.");return;}
            task.run();
        });
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(58));
        lp.bottomMargin=dp(7);parent.addView(b,lp);
        return b;
    }
    private void status(String value){
        runOnUiThread(()->{if(feedback!=null)feedback.setText(value);});
    }
    private void job(String title,Callable<String> process,Runnable done){
        if(working)return;
        working=true;status(title+"…");
        executor.execute(()->{
            String result;
            boolean okay;
            try{result=process.call();okay=true;}
            catch(Exception error){
                result="Operazione non riuscita ("+error.getClass().getSimpleName()+"): "+
                    (error.getMessage()==null?"nessun dettaglio":error.getMessage());
                okay=false;
            }
            final String message=result, finalResult=okay?"✓ "+message:"⚠ "+message;
            final boolean succeeded=okay;
            runOnUiThread(()->{
                working=false;
                if(succeeded && done!=null)done.run();
                status(finalResult);
            });
        });
    }
    private void draw(){
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
        root=vertical();root.setPadding(dp(17),dp(23),dp(17),dp(30));
        scroll.addView(root);setContentView(scroll);
        root.addView(text("❄  CLIMA ONE · PRO",26,WHITE,true));
        root.addView(text("Console diretta Wi-Fi · Modalità tecnico",13,TEXT,false));
        gap(root,10);
        LinearLayout search=panel(root,"01 / COLLEGAMENTO");
        action(search,"⌕  Cerca climatizzatori sulla LAN",true,this::discover);
        deviceList=vertical();search.addView(deviceList);
        livePanel=panel(root,"02 / MISURE RILEVATE");
        commandPanel=panel(root,"03 / REGOLAZIONI DIRETTE");
        featurePanel=panel(root,"04 / FUNZIONI NATIVE DEL FIRMWARE");
        LinearLayout footer=panel(root,"ESITO COMANDI E DIAGNOSTICA");
        feedback=text("Seleziona una macchina. Le chiavi V3 provengono dall'altra schermata della stessa app LAB.",14,WHITE,false);
        footer.addView(feedback);
        root.addView(text("I valori C1 sono da verificare con strumenti. Nessuna scrittura di frequenze inverter o giri oltre specifica.",12,TEXT,false));
        redrawPanels();
    }
    private void discover(){
        job("Cerco climatizzatori sul Wi-Fi",()->{
            WifiManager manager=(WifiManager)getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiManager.MulticastLock lock=manager==null?null:manager.createMulticastLock("clima_pro_udp");
            if(lock!=null){lock.setReferenceCounted(false);lock.acquire();}
            try{
                List<MideaLan.Device> found=MideaLan.discover();
                synchronized(devices){devices.clear();devices.addAll(found);}
                return found.size()+" climatizzatori riconosciuti.";
            }finally{if(lock!=null && lock.isHeld())lock.release();}
        },this::showDevices);
    }
    private void showDevices(){
        deviceList.removeAllViews();
        if(devices.isEmpty()){deviceList.addView(text("Nessuna unità trovata.",13,AMBER,false));return;}
        for(MideaLan.Device d:devices){
            action(deviceList,"❄  "+d.name+" · "+d.ip+" · V"+d.version,
                false,()->connect(d));
        }
    }
    private void connect(MideaLan.Device unit){
        job("Autentico e leggo "+unit.name,()->{
            if(unit.version==3){
                String[] keys=PrivateKeys.load(this,Long.toString(unit.id));
                if(keys==null||keys.length!=2)
                    throw new IllegalStateException("Chiavi V3 assenti. Apri prima CLIMA ONE LAB, collega il dispositivo e torna qui.");
                unit.token=keys[0];unit.key=keys[1];
            }
            MideaLan.State fresh=MideaLan.readState(unit);
            connected=unit;state=fresh;caps=null;readAt=System.currentTimeMillis();
            return "Sessione LAN autenticata, stato reale ricevuto.";
        },this::redrawPanels);
    }
    private void refresh(){
        if(connected==null){status("Scegli prima un dispositivo.");return;}
        MideaLan.Device unit=connected;
        job("Aggiorno la lettura LAN",()->{
            MideaLan.State fresh=MideaLan.readState(unit);
            if(connected==unit){state=fresh;readAt=System.currentTimeMillis();}
            return "Nuova lettura ricevuta.";
        },this::redrawPanels);
    }
    private String v(Double number,String unit){
        return number==null||Double.isNaN(number)||Double.isInfinite(number) ?
            "non disponibile":String.format(Locale.ITALY,"%.1f %s",number,unit);
    }
    private void redrawPanels(){
        livePanel.removeAllViews();
        commandPanel.removeAllViews();
        featurePanel.removeAllViews();
        livePanel.addView(text("STATO DELLA MACCHINA",13,BLUE,true));
        if(connected==null||state==null){
            livePanel.addView(text("In attesa di collegamento.",14,TEXT,false));
            commandPanel.addView(text("Attiva una connessione LAN autenticata.",14,TEXT,false));
            featurePanel.addView(text("Funzioni dopo la connessione.",14,TEXT,false));
            return;
        }
        MideaLan.State s=state;
        livePanel.addView(text((s.power?"ACCESO":"SPENTO")+"  •  Modalità "+s.mode+
            "  •  Setpoint "+String.format(Locale.ITALY,"%.1f °C",s.setpoint),18,WHITE,true));
        livePanel.addView(text("Ambiente: "+String.format(Locale.ITALY,"%.1f °C",s.indoor)+
           "  /  Esterno: "+String.format(Locale.ITALY,"%.1f °C",s.outdoor),13,TEXT,false));
        livePanel.addView(text("Compressore: "+v(s.compressorHz,"Hz")+
            "  /  Target: "+v(s.compressorTargetHz,"Hz"),14,WHITE,false));
        livePanel.addView(text("Ventola interna: "+v(s.fanRpm,"rpm")+
            "  /  Esterna: "+v(s.outdoorFanRpm,"rpm"),14,WHITE,false));
        livePanel.addView(text("Batteria interna: "+v(s.indoorCoilC,"°C")+
            "  /  Esterna: "+v(s.outdoorCoilC,"°C"),14,WHITE,false));
        livePanel.addView(text("Scarico compressore: "+v(s.dischargePipeC,"°C")+
            "  /  Potenza compressore C1/7: "+v(s.watts,"W"),14,WHITE,false));
        livePanel.addView(text("Potenza elettrica totale: NON MISURATA (necessario wattmetro).",12,AMBER,false));
        action(livePanel,"↻  Rileggi valori reali",true,this::refresh);
        action(livePanel,"🧰  Copia / condividi scheda tecnica",false,this::exportReport);

        commandPanel.addView(text("COMANDI STANDARD CON LETTURA DI CONFERMA",13,BLUE,true));
        action(commandPanel,s.power?"⏻  Spegni":"⏻  Accendi",true,
            ()->sendStandard("Accensione",x->x.power=!x.power));
        action(commandPanel,"🌡  Imposta temperatura (17–30 °C)",false,this::temperatureDialog);
        action(commandPanel,"〰  Velocità ventola interna (20–100%)",false,this::fanDialog);
        action(commandPanel,"♨  Seleziona modo FREDDO / CALDO / AUTO",false,this::modeDialog);
        action(commandPanel,"🍃  SUPER ECO · regolazione e verifica",true,this::superEcoDialog);
        action(commandPanel,"⚡  MAX / Turbo costruttore",false,this::turboDialog);

        featurePanel.addView(text("B5: capacità / B1: stato / B0: comandi consentiti",13,BLUE,true));
        action(featurePanel,"⌕  Scansiona funzioni native",true,this::inspect);
        if(caps==null){
            featurePanel.addView(text("Non ancora interrogate. Nessun comando proprietario inviato.",12,TEXT,false));
            return;
        }
        featurePanel.addView(text("BreezeleSS: "+flag(caps.breezeless)+
            "  •  Silenziosità esterna: "+flag(caps.outdoorSilent),13,WHITE,false));
        featurePanel.addView(text("GEAR: "+flag(caps.gearAvailable)+
            "  •  FAN custom: "+flag(caps.fanCustom),13,WHITE,false));
        if(caps.canBreeze()){
            if(s.mode==4)
                action(featurePanel,"❄  BreezeleSS in CALDO · prova tecnico",false,
                    ()->vendorDialog(AdvancedMidea.BREEZELESS,1,"BreezeleSS CALDO",true));
            if(s.mode==2)
                action(featurePanel,"❄  "+(Boolean.TRUE.equals(caps.breezeless)?"Disattiva":"Attiva")+" BreezeleSS",false,
                    ()->vendorDialog(AdvancedMidea.BREEZELESS,Boolean.TRUE.equals(caps.breezeless)?0:1,"BreezeleSS",false));
            action(featurePanel,"⏹  Disattiva BreezeleSS",false,
                ()->vendorDialog(AdvancedMidea.BREEZELESS,0,"Arresta BreezeleSS",false));
        }
        if(caps.canOutdoorSilent()){
            action(featurePanel,"🌙  "+(Boolean.TRUE.equals(caps.outdoorSilent)?"Disattiva":"Attiva")+" SILENT esterno",false,
                ()->vendorDialog(AdvancedMidea.OUTDOOR_SILENT,Boolean.TRUE.equals(caps.outdoorSilent)?0:3,
                    "Silenziosità esterna",false));
        }
        if(s.mode==2 && caps.canGear50()){
            action(featurePanel,"⚙  GEAR 50 / 75 / 100% · limite nativo",false,this::gearDialog);
        }
        featurePanel.addView(text("Frequenza compressore e ventola esterna: LETTURA. GEAR e SILENT modificano solo funzioni previste dal firmware.",12,AMBER,false));
    }
    private String flag(Boolean b){return b==null?"non rilevato":b?"attivo/sì":"spento/no";}
    private void sendStandard(String title,Consumer<MideaLan.State> change){
        if(connected==null||state==null)return;
        MideaLan.Device unit=connected;
        job("Invio "+title+" via Wi-Fi",()->{
            MideaLan.State before=MideaLan.readState(unit);
            MideaLan.State requested=before.copy();
            change.accept(requested);
            MideaLan.State after=MideaLan.apply(unit,requested);
            ArrayList<String> mismatches=new ArrayList<>();
            if(requested.power!=before.power&&after.power!=requested.power)mismatches.add("ON/OFF");
            if(requested.mode!=before.mode&&after.mode!=requested.mode)mismatches.add("modalità");
            if(Math.abs(requested.setpoint-before.setpoint)>0.1&&
               Math.abs(after.setpoint-requested.setpoint)>0.1)mismatches.add("setpoint");
            if(requested.fan!=before.fan&&after.fan!=requested.fan)mismatches.add("ventola interna");
            if(requested.turbo!=before.turbo&&after.turbo!=requested.turbo)mismatches.add("Turbo");
            if(connected==unit){state=after;readAt=System.currentTimeMillis();}
            return mismatches.isEmpty()?
                title+": stato riletto e campi modificati confermati.":
                title+": NON CONFERMATI "+mismatches+". Controlla i valori riletti.";
        },this::redrawPanels);
    }
    private void temperatureDialog(){
        LinearLayout body=vertical();body.setPadding(dp(18),dp(10),dp(18),dp(7));
        TextView value=text("",22,Color.BLACK,true);body.addView(value);
        SeekBar bar=new SeekBar(this);bar.setMax(26);
        int initial=Math.max(0,Math.min(26,(int)Math.round((state.setpoint-17)*2)));
        bar.setProgress(initial);body.addView(bar);
        value.setText(String.format(Locale.ITALY,"%.1f °C",17+initial*0.5));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar b,int p,boolean user){
                value.setText(String.format(Locale.ITALY,"%.1f °C",17+p*0.5));
            }
            public void onStartTrackingTouch(SeekBar b){}
            public void onStopTrackingTouch(SeekBar b){}
        });
        new AlertDialog.Builder(this).setTitle("Regola setpoint")
            .setView(body).setNegativeButton("Annulla",null)
            .setPositiveButton("Invia e verifica",(d,w)->{
                final double target=17+bar.getProgress()*0.5;
                sendStandard("Temperatura",x->x.setpoint=target);
            }).show();
    }
    private void fanDialog(){
        boolean custom=caps!=null&&Boolean.TRUE.equals(caps.fanCustom);
        int step=custom?5:20, minimum=20;
        LinearLayout body=vertical();body.setPadding(dp(18),dp(10),dp(18),dp(5));
        TextView value=text("",22,Color.BLACK,true);body.addView(value);
        SeekBar bar=new SeekBar(this);bar.setMax((100-minimum)/step);
        int n=(int)Math.round((Math.max(20,Math.min(100,state.fan))-minimum)/(double)step);
        bar.setProgress(n);body.addView(bar);
        value.setText((minimum+n*step)+"%");
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar seek,int p,boolean user){
                value.setText((minimum+p*step)+"%");
            }
            public void onStartTrackingTouch(SeekBar seek){}
            public void onStopTrackingTouch(SeekBar seek){}
        });
        body.addView(text(custom?"Personalizzata ogni 5%: supporto B5 rilevato.":
            "Velocità standard ogni 20%. Cerca prima le capacità B5 per la personalizzata.",
            12,Color.DKGRAY,false));
        new AlertDialog.Builder(this).setTitle("Ventola interna · 20–100%")
            .setView(body).setNegativeButton("Annulla",null)
            .setNeutralButton("AUTO",(d,w)->sendStandard("Ventola AUTO",x->x.fan=102))
            .setPositiveButton("Invia e verifica",(d,w)->{
                final int fan=minimum+bar.getProgress()*step;
                sendStandard("Ventola interna",x->x.fan=fan);
            }).show();
    }
    private void modeDialog(){
        String[] modes={"Auto","Raffrescamento","Deumidifica","Riscaldamento","Solo ventilazione"};
        new AlertDialog.Builder(this).setTitle("Modalità climatizzatore")
            .setSingleChoiceItems(modes,Math.max(0,Math.min(4,state.mode-1)),
                (d,index)->{d.dismiss();final int next=index+1;sendStandard("Modalità",x->x.mode=next);})
            .setNegativeButton("Annulla",null).show();
    }
    private void turboDialog(){
        if(state.mode!=2&&state.mode!=4){status("Turbo soltanto in riscaldamento o raffrescamento.");return;}
        boolean support=caps!=null&&Boolean.TRUE.equals(state.mode==4?caps.turboHeat:caps.turboCool);
        if(!support){status("Prima scansiona B5: Turbo non ancora dichiarato in questa modalità.");return;}
        new AlertDialog.Builder(this).setTitle("MAX nativo / Turbo")
            .setMessage("Non è un comando 150%: Turbo usa il massimo previsto dal firmware e può aumentare i consumi.")
            .setNegativeButton("Annulla",null).setPositiveButton("Applica",(d,w)->
                sendStandard("Turbo",x->{x.turbo=!x.turbo;if(x.turbo)x.eco=false;})).show();
    }
    private void inspect(){
        if(connected==null)return;
        MideaLan.Device unit=connected;
        job("Rilevo capability e stati B5/B1",()->{
            AdvancedMidea.Snapshot detected=AdvancedMidea.inspect(unit);
            if(connected==unit)caps=detected;
            return "Funzioni native interrogate. Non rilevato non significa non supportato.";
        },this::redrawPanels);
    }
    private void gearDialog(){
        if(caps==null || !caps.canGear50() || state.mode!=2)return;
        String[] levels={"50%","75%","100% (normale)"};
        int[] codes={50,75,100};
        new AlertDialog.Builder(this).setTitle("GEAR nativo Midea")
            .setItems(levels,(d,which)->vendorDialog(AdvancedMidea.GEAR,codes[which],
                "GEAR "+codes[which]+"%",false))
            .setNegativeButton("Annulla",null).show();
    }
    private void vendorDialog(int tag,int value,String name,boolean heatTest){
        if(connected==null || state==null || caps==null)return;
        if(heatTest && state.mode!=4){status("Per BreezeleSS CALDO serve riscaldamento attivo.");return;}
        if(tag==AdvancedMidea.BREEZELESS&&!caps.canBreeze())return;
        if(tag==AdvancedMidea.OUTDOOR_SILENT&&!caps.canOutdoorSilent())return;
        if(tag==AdvancedMidea.GEAR && (state.mode!=2||!caps.canGear50()))return;
        String message="Usa un comando vendor Midea noto, con lettura B1 di verifica. "+
            "Non scrive parametri diretti dell'inverter o della ventola esterna.";
        if(heatTest)message="TEST SPERIMENTALE: BreezeleSS in CALDO può essere ignorato dal firmware. "+
            "Controlla i deflettori e interrompi se il comportamento è anomalo. "+
            "L'eventuale conferma del bit B1 NON certifica il funzionamento fisico.\n\n"+message;
        MideaLan.Device unit=connected;
        new AlertDialog.Builder(this).setTitle(name).setMessage(message)
            .setNegativeButton("Annulla",null)
            .setPositiveButton("Invia e leggi",(d,w)->job("Imposto "+name,()->{
                int readback=AdvancedMidea.applyVerified(unit,tag,value);
                MideaLan.State fresh=MideaLan.readState(unit);
                AdvancedMidea.Snapshot advanced=AdvancedMidea.inspect(unit);
                if(connected==unit){state=fresh;caps=advanced;readAt=System.currentTimeMillis();}
                return name+": tag B1 restituisce "+readback+
                    ". Verificare sul climatizzatore l'effetto.";
            },this::redrawPanels)).show();
    }
    private void superEcoDialog(){
        if(connected==null||state==null)return;
        final MideaLan.State planned;
        try{planned=SafeEco.superEco(state);}catch(Exception e){status(e.getMessage());return;}
        new AlertDialog.Builder(this).setTitle("SUPER ECO · modalità tecnici")
            .setMessage("Setpoint "+String.format(Locale.ITALY,"%.1f",planned.setpoint)+
                " °C, ventola interna 20%, Turbo spento. Proverà GEAR 50% in FREDDO "+
                "e silent esterno SOLO se riconosciuti. Non imposta giri minimi o frequenze "+
                "di fabbrica; il risparmio va misurato.")
            .setNegativeButton("Annulla",null)
            .setPositiveButton("Applica e verifica",(d,w)->{
                MideaLan.Device unit=connected;
                job("Applico SUPER ECO",()->{
                    AdvancedMidea.Snapshot nativeState=null;
                    try{nativeState=AdvancedMidea.inspect(unit);}catch(Exception ignored){}
                    MideaLan.State start=MideaLan.readState(unit);
                    MideaLan.State after=MideaLan.apply(unit,SafeEco.superEco(start));
                    StringBuilder result=new StringBuilder("Setpoint "+after.setpoint+
                        " °C, fan "+after.fan+"%. ");
                    if(start.mode==2&&nativeState!=null&&nativeState.canGear50()){
                        try{AdvancedMidea.applyVerified(unit,AdvancedMidea.GEAR,50);
                            result.append("GEAR 50% confermato. ");}
                        catch(Exception ex){result.append("GEAR non confermato. ");}
                    }
                    if(nativeState!=null&&nativeState.canOutdoorSilent()){
                        try{AdvancedMidea.applyVerified(unit,AdvancedMidea.OUTDOOR_SILENT,3);
                            result.append("Silent esterno confermato. ");}
                        catch(Exception ex){result.append("Silent esterno non confermato. ");}
                    }
                    MideaLan.State fresh=MideaLan.readState(unit);
                    if(connected==unit){state=fresh;readAt=System.currentTimeMillis();}
                    return result.append("Verifica consumi e comfort nel tempo.").toString();
                },this::redrawPanels);
            }).show();
    }
    private void exportReport(){
        if(connected==null)return;
        String result=TechnicianReport.build(connected,state,readAt)+
            "\nCONTROLLO VENDOR MIDEA (non certificato)\n"+
            (caps==null?"Capacità non interrogate\n":
                "BreezeleSS: "+flag(caps.breezeless)+
                " / Silent esterno: "+flag(caps.outdoorSilent)+
                " / GEAR: "+flag(caps.gearAvailable)+"\n");
        new AlertDialog.Builder(this).setTitle("Scheda tecnico")
            .setMessage("Dati tecnici sperimentali. Il report non contiene token, key o password.")
            .setNegativeButton("Chiudi",null)
            .setNeutralButton("Copia",(d,w)->{
                ClipboardManager cb=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
                cb.setPrimaryClip(ClipData.newPlainText("CLIMA ONE PRO report",result));
                status("Report copiato senza credenziali.");
            })
            .setPositiveButton("Condividi",(d,w)->{
                Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");
                i.putExtra(Intent.EXTRA_TEXT,result);i.putExtra(Intent.EXTRA_SUBJECT,"CLIMA ONE PRO - rapporto");
                startActivity(Intent.createChooser(i,"Condividi report"));
            }).show();
    }
    @Override public void onDestroy(){executor.shutdownNow();super.onDestroy();}
}
