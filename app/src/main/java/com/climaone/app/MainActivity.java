package com.climaone.app;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.wifi.WifiManager;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import com.climaone.app.core.MideaLan;
import com.climaone.app.core.SmartHomeCloud;
import com.climaone.app.core.PrivateKeys;
import com.climaone.app.core.TechnicianReport;
import java.util.*;
import java.util.concurrent.*;

/** CLIMA ONE Android native. Real LAN I/O, no embedded browser, no local gateway. */
public class MainActivity extends Activity {
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final ArrayList<MideaLan.Device> devices=new ArrayList<>();
    private MideaLan.Device active;
    private MideaLan.State current;
    private LinearLayout content,unitBox,controlBox,reportBox;
    private TextView statusText,headerSubtitle;
    private final int NAVY=Color.rgb(10,22,34),CARD=Color.rgb(23,42,57),WHITE=Color.WHITE,
            MUTED=Color.rgb(163,184,200),CYAN=Color.rgb(97,222,220),ORANGE=Color.rgb(249,190,101);
    private boolean busy=false;
    private boolean technicianMode=false;
    private volatile long lastReadAt=0;

    @Override public void onCreate(Bundle b){super.onCreate(b);
        getWindow().setStatusBarColor(NAVY);getWindow().setNavigationBarColor(NAVY);
        technicianMode=getPreferences(MODE_PRIVATE).getBoolean("technician_mode",false);
        render();
    }
    private GradientDrawable shape(int fill,int radius){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(radius));return d;}
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density);}
    private TextView label(String text,int sp,int color,boolean bold){TextView t=new TextView(this);t.setText(text);t.setTextColor(color);t.setTextSize(sp);if(bold)t.setTypeface(null,Typeface.BOLD);t.setPadding(0,dp(3),0,dp(3));return t;}
    private LinearLayout vertical(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private void space(LinearLayout parent,int h){View x=new View(this);parent.addView(x,new LinearLayout.LayoutParams(1,dp(h)));}
    private void card(LinearLayout p,LinearLayout v){v.setPadding(dp(18),dp(15),dp(18),dp(15));v.setBackground(shape(CARD,20));p.addView(v,new LinearLayout.LayoutParams(-1,-2));}
    private Button button(String text,Runnable onClick,boolean emphasis){Button b=new Button(this);b.setAllCaps(false);b.setText(text);b.setTextColor(emphasis?NAVY:WHITE);b.setTextSize(15);b.setBackground(shape(emphasis?CYAN:Color.rgb(48,74,89),16));b.setOnClickListener(v->{if(!busy)onClick.run();});return b;}
    private void btn(LinearLayout p,String text,Runnable onClick,boolean emphasis){Button b=button(text,onClick,emphasis);p.addView(b,new LinearLayout.LayoutParams(-1,dp(54)));space(p,7);}
    private void message(String text){runOnUiThread(()->{statusText.setText(text);});}
    private void busyTask(String heading,Callable<?> work,Runnable onSuccess){
        if(busy)return;busy=true;message(heading+"…");
        io.execute(()->{try{work.call();runOnUiThread(()->{busy=false;if(onSuccess!=null)onSuccess.run();});}
            catch(Exception e){runOnUiThread(()->{busy=false;message("Errore: "+e.getMessage());});}});
    }
    private void render(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(NAVY);
        content=vertical();content.setPadding(dp(18),dp(25),dp(18),dp(30));scroll.addView(content);setContentView(scroll);
        content.addView(label("❄  CLIMA ONE",28,WHITE,true));
        headerSubtitle=label(technicianMode?"Modalita tecnico · Diagnostica in sola lettura":"Wi-Fi per climatizzatori Midea compatibili",13,MUTED,false);content.addView(headerSubtitle);
        space(content,10);
        btn(content,technicianMode?"🧰  Modalita tecnico ATTIVA · Torna a Cliente":"🧰  Apri modalita TECNICO",this::toggleTechnicianMode,false);
        space(content,9);
        LinearLayout search=vertical();card(content,search);
        search.addView(label("RICERCA CLIMATIZZATORI",13,CYAN,true));
        search.addView(label("Midea · OEM compatibili · protocolli V2/V3",13,MUTED,false));
        space(search,7);btn(search,"⌕  Trova climatizzatori Wi-Fi",this::discover,true);
        btn(search,"Cerca tramite indirizzo IP",this::manualIp,false);
        space(content,12);
        unitBox=vertical();content.addView(unitBox);
        space(content,12);
        controlBox=vertical();content.addView(controlBox);
        space(content,12);
        reportBox=vertical();content.addView(reportBox);
        space(content,12);
        LinearLayout state=vertical();card(content,state);
        state.addView(label("STATO COLLEGAMENTO",12,CYAN,true));
        statusText=label("Collega il telefono alla stessa rete Wi-Fi dei climatizzatori, poi tocca Trova.",14,WHITE,false);state.addView(statusText);
        space(content,13);
        content.addView(label("Modalità test: comandi reali soltanto dopo una connessione autenticata. Firmware e limiti inverter non vengono modificati.",12,MUTED,false));
    }
    private void toggleTechnicianMode(){
        technicianMode=!technicianMode;
        getPreferences(MODE_PRIVATE).edit().putBoolean("technician_mode",technicianMode).apply();
        render();
        if(!devices.isEmpty())drawDevices();
        if(active!=null)showSelected();
    }
    private void showTechnicianReport(){
        if(active==null)return;
        final String report=TechnicianReport.build(active,current,lastReadAt);
        ScrollView scroll=new ScrollView(this);
        TextView lines=new TextView(this);lines.setText(report);lines.setTextSize(13);
        lines.setTextIsSelectable(true);
        lines.setPadding(dp(16),dp(12),dp(16),dp(12));
        scroll.addView(lines);
        new AlertDialog.Builder(this)
            .setTitle("Scheda TECNICO · Solo lettura")
            .setView(scroll)
            .setNegativeButton("Chiudi",(d,w)->{})
            .setNeutralButton("Copia",(d,w)->{
                android.content.ClipboardManager clip=(android.content.ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
                clip.setPrimaryClip(ClipData.newPlainText("CLIMA ONE · Scheda tecnica",report));
                message("Scheda tecnica copiata. Controlla i dati prima di condividerli.");
            })
            .setPositiveButton("Condividi",(d,w)->{
                Intent out=new Intent(Intent.ACTION_SEND);
                out.setType("text/plain");out.putExtra(Intent.EXTRA_SUBJECT,"CLIMA ONE · Scheda tecnica");
                out.putExtra(Intent.EXTRA_TEXT,report);
                startActivity(Intent.createChooser(out,"Condividi scheda tecnica"));
            }).show();
    }
    private void manualIp(){
        EditText ip=new EditText(this);ip.setSingleLine(true);ip.setHint("192.168.1.50");
        new AlertDialog.Builder(this).setTitle("Ricerca per IP locale").setMessage("Usa l’indirizzo del climatizzatore sulla tua rete Wi-Fi.").setView(ip).setNegativeButton("Annulla",null).setPositiveButton("Cerca",(d,w)->{
            final String address=ip.getText().toString().trim();
            busyTask("Cerco il climatizzatore a "+address,()->{
                WifiManager wm=(WifiManager)getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                WifiManager.MulticastLock lock=wm.createMulticastLock("clima_one_unicast");lock.setReferenceCounted(false);lock.acquire();
                try{List<MideaLan.Device> found=MideaLan.discoverTarget(address);for(MideaLan.Device unit:found){
                     String[] tk=PrivateKeys.load(this,Long.toString(unit.id));if(tk!=null){unit.token=tk[0];unit.key=tk[1];}
                     boolean exists=false;for(MideaLan.Device old:devices)if(old.ip.equals(unit.ip)){exists=true;break;}if(!exists)devices.add(unit);
                }}finally{lock.release();}return null;
            },()->{drawDevices();message("Ricerca IP terminata. Dispositivi totali: "+devices.size());});
        }).show();
    }
    private void discover(){
        busyTask("Cerco dispositivi nella rete locale",()->{
            WifiManager wm=(WifiManager)getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiManager.MulticastLock lock=wm.createMulticastLock("clima_one_scan");lock.setReferenceCounted(false);lock.acquire();
            try{List<MideaLan.Device> found=MideaLan.discover();devices.clear();devices.addAll(found);}finally{lock.release();}
            for(MideaLan.Device d:devices)if(d.version==3){try{String[] tk=PrivateKeys.load(this,Long.toString(d.id));if(tk!=null){d.token=tk[0];d.key=tk[1];}}catch(Exception ignored){}}
            return null;
        },()->{drawDevices();message(devices.isEmpty()?"Nessun Midea compatibile trovato. Verifica rete 2,4 GHz, isolamento client e che lo split sia online.":devices.size()+" climatizzatore/i rilevati sulla rete locale.");});
    }
    private void drawDevices(){
        unitBox.removeAllViews();controlBox.removeAllViews();reportBox.removeAllViews();
        LinearLayout box=vertical();card(unitBox,box);box.addView(label("DISPOSITIVI RILEVATI",14,CYAN,true));
        for(MideaLan.Device d:devices){
            btn(box,"❄  "+d.name+" · "+d.ip+" · V"+d.version,()->choose(d),d==active);
        }
        if(devices.isEmpty())box.addView(label("Ancora nessun dispositivo.",14,MUTED,false));
    }
    private void choose(MideaLan.Device d){active=d;current=null;lastReadAt=0;drawDevices();showSelected();}
    private void showSelected(){
        controlBox.removeAllViews();reportBox.removeAllViews();
        if(active==null)return;
        LinearLayout box=vertical();card(controlBox,box);
        box.addView(label("CLIMATIZZATORE",12,CYAN,true));
        box.addView(label(active.name,22,WHITE,true));box.addView(label("IP locale "+active.ip+" · protocollo V"+active.version,13,MUTED,false));
        space(box,7);
        if(technicianMode){
            box.addView(label("La ricerca LAN identifica modulo e protocollo, non il modello commerciale o il circuito frigorifero.",13,MUTED,false));
            btn(box,"🧰  Scheda tecnico · Copia / Condividi",this::showTechnicianReport,false);
        }
        if(active.version==3&&(active.token.isEmpty()||active.key.isEmpty())){
            box.addView(label("Questa unità richiede autenticazione V3: collega SmartHome oppure importa token e chiave validi.",13,ORANGE,false));
            btn(box,"Collega account SmartHome",this::smartHomeDialog,true);
            btn(box,"Importa token + key",this::tokenDialog,false);
        }else{
            btn(box,"↻  Leggi stato reale",this::read,true);
            if(active.version==3)btn(box,"Aggiorna token / key",this::tokenDialog,false);
        }
        if(current!=null)showControls();
    }
    private void smartHomeDialog(){if(active==null)return;
        LinearLayout fields=vertical();fields.setPadding(dp(16),dp(8),dp(16),dp(4));
        EditText account=new EditText(this);account.setHint("Email account SmartHome");account.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);fields.addView(account);
        EditText pw=new EditText(this);pw.setHint("Password SmartHome");pw.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);fields.addView(pw);
        new AlertDialog.Builder(this).setTitle("Autenticazione Midea SmartHome").setMessage("Credenziali inviate solo al servizio Midea tramite HTTPS. Non vengono salvate da CLIMA ONE. Il climatizzatore deve essere associato all'account.")
            .setView(fields).setNegativeButton("Annulla",null).setPositiveButton("Collega",(dia,which)->{
                String email=account.getText().toString(),pass=pw.getText().toString();
                busyTask("Autenticazione SmartHome",()->{
                    SmartHomeCloud cloud=new SmartHomeCloud();cloud.login(email,pass);
                    String[] values=cloud.tokenKey(active.id);
                    active.token=values[0];active.key=values[1];
                    PrivateKeys.save(this,Long.toString(active.id),active.token,active.key);
                    return null;
                },()->{message("Token Midea ricevuto e protetto sul dispositivo. Ora puoi leggere lo stato.");showSelected();});
            }).show();
    }
    private void tokenDialog(){if(active==null)return;
        LinearLayout fields=vertical();fields.setPadding(dp(15),dp(6),dp(15),0);
        EditText token=new EditText(this);token.setHint("Token 128 caratteri hex");token.setSingleLine(false);fields.addView(token);
        EditText key=new EditText(this);key.setHint("Key 64 caratteri hex");key.setSingleLine(false);fields.addView(key);
        new AlertDialog.Builder(this).setTitle("Importa credenziali V3").setMessage("Solo token e chiave autorizzati della tua unità. Non condividere questi valori nelle chat.")
            .setView(fields).setNegativeButton("Annulla",null).setPositiveButton("Salva",(dlg,which)->{
                String t=token.getText().toString().trim(),k=key.getText().toString().trim();
                try{if(MideaLan.hex(t).length!=64||MideaLan.hex(k).length!=32)throw new IllegalArgumentException("Lunghezza errata");
                    active.token=t;active.key=k;PrivateKeys.save(this,Long.toString(active.id),t,k);message("Credenziali memorizzate in modo cifrato.");showSelected();
                }catch(Exception e){message("Credenziali non valide: "+e.getMessage());}
            }).show();
    }
    private void read(){if(active==null)return;
        MideaLan.Device selected=active;
        busyTask("Interrogo il climatizzatore",()->{MideaLan.State state=MideaLan.readState(selected);if(active==selected){current=state;lastReadAt=System.currentTimeMillis();}return null;},
            ()->{message("Dati ricevuti direttamente dalla macchina.");showSelected();});
    }
    private void doControl(java.util.function.Consumer<MideaLan.State> change){
        if(active==null||current==null)return;
        MideaLan.Device selected=active;
        busyTask("Invio comando e verifico risposta",()->{
            MideaLan.State latest=MideaLan.readState(selected);change.accept(latest);
            MideaLan.State after=MideaLan.apply(selected,latest);
            if(active==selected){current=after;lastReadAt=System.currentTimeMillis();}
            return null;
        },()->{message("Comando inviato. Stato riletto dal climatizzatore.");showSelected();});
    }
    private String num(double n){return Double.isNaN(n)?"non disponibile":String.format(Locale.ITALY,"%.1f",n);}
    private String nice(String name,Double value,String unit){return name+": "+(value==null?"non disponibile":String.format(Locale.ITALY,"%.1f %s",value,unit));}
    private String modeName(int m){switch(m){case 1:return "Auto";case 2:return "Freddo";case 3:return "Deumidifica";case 4:return "Caldo";case 5:return "Ventilazione";default:return "Modalità "+m;}}
    private void showControls(){
        reportBox.removeAllViews();
        LinearLayout control=vertical();card(reportBox,control);
        control.addView(label("STATO LIVE",12,CYAN,true));
        control.addView(label((current.power?"ACCESO":"SPENTO")+"  ·  "+modeName(current.mode),22,WHITE,true));
        control.addView(label("Temperatura richiesta: "+num(current.setpoint)+" °C",19,WHITE,true));
        control.addView(label("Ambiente: "+num(current.indoor)+" °C  ·  Esterno: "+num(current.outdoor)+" °C",14,MUTED,false));
        control.addView(label("Ventilatore: "+(current.fan==102?"Auto":current.fan+"%")+"  ·  ECO: "+(current.eco?"sì":"no"),14,MUTED,false));
        space(control,8);
        btn(control,current.power?"⏻  Spegni":"⏻  Accendi",()->doControl(s->s.power=!s.power),true);
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
        Button minus=button("− 0,5 °C",()->doControl(s->s.setpoint=Math.max(17,s.setpoint-0.5)),false);
        Button plus=button("+ 0,5 °C",()->doControl(s->s.setpoint=Math.min(30,s.setpoint+0.5)),false);
        row.addView(minus,new LinearLayout.LayoutParams(0,dp(54),1));View gap=new View(this);row.addView(gap,new LinearLayout.LayoutParams(dp(8),1));row.addView(plus,new LinearLayout.LayoutParams(0,dp(54),1));control.addView(row);
        space(control,12);
        control.addView(label("MODALITÀ",12,CYAN,true));
        Spinner modes=new Spinner(this);ArrayAdapter<String> ma=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Auto","Freddo","Deumidifica","Caldo","Ventilazione"});modes.setAdapter(ma);modes.setSelection(Math.max(0,Math.min(current.mode-1,4)));control.addView(modes);
        btn(control,"Applica modalità scelta",()->{int selected=modes.getSelectedItemPosition()+1;doControl(s->s.mode=selected);},false);
        control.addView(label("VENTILAZIONE",12,CYAN,true));
        Spinner fans=new Spinner(this);String[] opts={"Auto","Bassa 40%","Media 60%","Alta 100%"};
        Spinner fanSpinner=fans;ArrayAdapter<String> fa=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,opts);fanSpinner.setAdapter(fa);control.addView(fanSpinner);
        btn(control,"Applica ventilazione",()->{int[] values={102,40,60,100};int newFan=values[fanSpinner.getSelectedItemPosition()];doControl(s->s.fan=newFan);},false);
        btn(control,current.eco?"Disattiva ECO":"Attiva ECO",()->doControl(s->s.eco=!s.eco),false);
        space(reportBox,12);
        LinearLayout diag=vertical();card(reportBox,diag);diag.addView(label(technicianMode?"DIAGNOSTICA TECNICO · DATI DA VALIDARE":"TELEMETRIA DISPONIBILE",12,CYAN,true));
        diag.addView(label(nice("Compressore",current.compressorHz,"Hz"),15,WHITE,false));
        diag.addView(label(nice("Frequenza obiettivo",current.compressorTargetHz,"Hz"),15,WHITE,false));
        diag.addView(label(nice("Ventilatore",current.fanRpm,"rpm equivalenti"),15,WHITE,false));
        diag.addView(label(nice("Potenza stimata dal protocollo",current.watts,"W"),15,WHITE,false));
        diag.addView(label(nice("Energia ciclo riportata",current.kwh,"kWh"),15,WHITE,false));
        if(current.kwh!=null){
            final float price=getPreferences(MODE_PRIVATE).getFloat("cost_kwh",0.25f);
            diag.addView(label("Costo energia ciclo: "+String.format(Locale.ITALY,"€ %.2f",current.kwh*price)+"  (tariffa "+String.format(Locale.ITALY,"%.3f",price)+" €/kWh)",15,CYAN,true));
        }
        btn(diag,"Imposta tariffa €/kWh",this::tariffDialog,false);
        if(technicianMode){
            btn(diag,"🧰  Genera scheda diagnostica",this::showTechnicianReport,true);
            diag.addView(label("Pressioni, surriscaldamento e quantita refrigerante NON sono misurati dalla LAN.",12,ORANGE,false));
        }
        diag.addView(label("I sensori possono non essere supportati; valori non verificati dal produttore.",12,MUTED,false));
    }
    private void tariffDialog(){
        EditText value=new EditText(this);value.setSingleLine(true);value.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);value.setText(String.valueOf(getPreferences(MODE_PRIVATE).getFloat("cost_kwh",0.25f)));
        new AlertDialog.Builder(this).setTitle("Tariffa elettrica").setMessage("Prezzo variabile in €/kWh. Non comprende i costi fissi della bolletta.").setView(value).setNegativeButton("Annulla",null).setPositiveButton("Salva",(d,w)->{
            try{float v=Float.parseFloat(value.getText().toString().replace(",","."));if(v<0||v>3)throw new IllegalArgumentException();getPreferences(MODE_PRIVATE).edit().putFloat("cost_kwh",v).apply();showSelected();}
            catch(Exception ex){message("Inserisci una tariffa compresa tra 0 e 3 €/kWh.");}
        }).show();
    }
    @Override public void onDestroy(){io.shutdownNow();super.onDestroy();}
}
