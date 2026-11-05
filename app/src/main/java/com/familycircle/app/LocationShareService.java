package com.familycircle.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import java.util.HashMap;
import java.util.Map;

public class LocationShareService extends Service {
    public static final String ACTION_START="com.familycircle.app.START_SHARING";
    public static final String ACTION_STOP="com.familycircle.app.STOP_SHARING";
    public static final String ACTION_PAUSE="com.familycircle.app.PAUSE_SHARING";
    public static final String ACTION_RESUME="com.familycircle.app.RESUME_SHARING";
    public static final String EXTRA_PAUSE_UNTIL="pauseUntil";
    private static final String CHANNEL="location_sharing";
    private static final String STATUS_CHANNEL="sharing_status";
    private FusedLocationProviderClient fused;
    private LocationCallback callback;
    private DatabaseReference locationRef;
    private DatabaseReference familyRef;
    private ValueEventListener safeAreaListener;
    private final Map<String,SafePlace> safePlaces=new HashMap<>();
    private String activeSafeAreaName="";
    private boolean trackingInsideSafeArea;
    private Location lastLocation;
    private static final long OUTSIDE_INTERVAL_MS=30_000L;
    private static final long INSIDE_INTERVAL_MS=4L*60L*1000L;
    private final Handler timerHandler=new Handler(Looper.getMainLooper());
    private Runnable autoResumeTask;
    private long pauseUntil;
    private boolean batteryReceiverRegistered;
    private final BroadcastReceiver batteryReceiver=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){if(Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction()))handleBatteryChanged(intent);}
    };
    private static final class SafePlace {
        final String id,name; final double lat,lng,radius; final long version;
        SafePlace(String id,String name,double lat,double lng,double radius,long version){this.id=id;this.name=name;this.lat=lat;this.lng=lng;this.radius=radius;this.version=version;}
    }

    @Override public void onCreate(){super.onCreate();fused=LocationServices.getFusedLocationProviderClient(this);createChannel();IntentFilter batteryFilter=new IntentFilter(Intent.ACTION_BATTERY_CHANGED);if(Build.VERSION.SDK_INT>=33)registerReceiver(batteryReceiver,batteryFilter,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(batteryReceiver,batteryFilter);batteryReceiverRegistered=true;}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        String action=intent==null?ACTION_START:intent.getAction();
        if(ACTION_STOP.equals(action)){clearAndStop();return START_NOT_STICKY;}
        if(ACTION_PAUSE.equals(action)){
            pauseUntil=intent==null?0L:intent.getLongExtra(EXTRA_PAUSE_UNTIL,0L);
            if(pauseUntil<=System.currentTimeMillis()||pauseUntil>System.currentTimeMillis()+2L*60L*60L*1000L){clearAndStop();notifyStatus("Pause not scheduled","Choose a future time within the next 2 hours.");return START_NOT_STICKY;}
            getSharedPreferences("FamilyCircle",MODE_PRIVATE).edit().putLong("pauseUntil",pauseUntil).apply();
            getSharedPreferences("FamilyCircle",MODE_PRIVATE).edit().putBoolean("sharingEnabled",true).apply();
            if(!startForegroundSafely()){clearAndStop();return START_NOT_STICKY;}
            if(!initializeFamilyReferences()){clearAndStop();return START_NOT_STICKY;}
            publishLowBatteryIfNeeded(currentBatteryPercent());
            pauseLocationUpdates();scheduleAutoResume();updateForegroundNotification();return START_STICKY;
        }
        long storedPauseUntil=getSharedPreferences("FamilyCircle",MODE_PRIVATE).getLong("pauseUntil",0L);
        long now=System.currentTimeMillis();
        boolean oldPauseExceedsLimit=storedPauseUntil>now+2L*60L*60L*1000L;
        boolean shouldNotifyResumed=ACTION_RESUME.equals(action)||(storedPauseUntil>0L&&storedPauseUntil<=now)||oldPauseExceedsLimit;
        if(ACTION_RESUME.equals(action)||oldPauseExceedsLimit)pauseUntil=0L;else pauseUntil=storedPauseUntil;
        if(ACTION_RESUME.equals(action)||oldPauseExceedsLimit)getSharedPreferences("FamilyCircle",MODE_PRIVATE).edit().putLong("pauseUntil",0L).apply();
        if(!startForegroundSafely()){clearAndStop();return START_NOT_STICKY;}
        if(!initializeFamilyReferences()){clearAndStop();return START_NOT_STICKY;}
        getSharedPreferences("FamilyCircle",MODE_PRIVATE).edit().putBoolean("sharingEnabled",true).apply();
        publishLowBatteryIfNeeded(currentBatteryPercent());
        if(pauseUntil>System.currentTimeMillis()){scheduleAutoResume();updateForegroundNotification();return START_STICKY;}
        pauseUntil=0L;getSharedPreferences("FamilyCircle",MODE_PRIVATE).edit().putLong("pauseUntil",0L).apply();
        if(autoResumeTask!=null){timerHandler.removeCallbacks(autoResumeTask);autoResumeTask=null;}
        begin();updateForegroundNotification();if(shouldNotifyResumed)notifySharingResumed();return START_STICKY;
    }
    private boolean startForegroundSafely(){
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED&&checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED){notifyResumeNeedsPermission();return false;}
        if(Build.VERSION.SDK_INT>=29)startForeground(7,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);else startForeground(7,notification());return true;
    }
    private boolean initializeFamilyReferences(){
        String family=getSharedPreferences("FamilyCircle",MODE_PRIVATE).getString("familyId","");
        String uid=FirebaseAuth.getInstance().getUid();
        if(family.isEmpty()||uid==null)return false;
        familyRef=FirebaseDatabase.getInstance(getString(R.string.firebase_database_url)).getReference("families").child(family);
        locationRef=familyRef.child("locations").child(uid);
        return true;
    }
    private void begin(){
        String uid=FirebaseAuth.getInstance().getUid();
        if(uid==null||familyRef==null||locationRef==null){clearAndStop();return;}
        safeAreaListener=new ValueEventListener(){
            public void onDataChange(DataSnapshot s){
                safePlaces.clear();
                if(s.hasChild("name")) addSafePlace(s,"legacy");
                else for(DataSnapshot place:s.getChildren())addSafePlace(place,place.getKey()==null?"place":place.getKey());
                if(lastLocation!=null)checkSafeArea(lastLocation);
                else if(safePlaces.isEmpty())setLocationCadence(false);
            }
            public void onCancelled(DatabaseError e){}
        };
        familyRef.child("safeAreas").child(uid).addValueEventListener(safeAreaListener);
        setLocationCadence(false);
    }
    private void addSafePlace(DataSnapshot data,String id){
        if(!Boolean.TRUE.equals(data.child("enabled").getValue(Boolean.class)))return;
        Double lat=data.child("lat").getValue(Double.class),lng=data.child("lng").getValue(Double.class);
        Long radius=data.child("radiusMeters").getValue(Long.class),version=data.child("updatedAt").getValue(Long.class);
        String name=data.child("name").getValue(String.class);
        if(lat!=null&&lng!=null&&radius!=null&&version!=null)safePlaces.put(id,new SafePlace(id,name==null?"Safe area":name,lat,lng,radius.doubleValue(),version));
    }
    private void setLocationCadence(boolean insideSafeArea){
        if(callback!=null){fused.removeLocationUpdates(callback);callback=null;}
        trackingInsideSafeArea=insideSafeArea;
        long interval=insideSafeArea?INSIDE_INTERVAL_MS:OUTSIDE_INTERVAL_MS;
        LocationRequest request=new LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY,interval)
                .setMinUpdateIntervalMillis(interval)
                .setMinUpdateDistanceMeters(insideSafeArea?0:25)
                .build();
        callback=new LocationCallback(){@Override public void onLocationResult(LocationResult result){if(result!=null&&result.getLastLocation()!=null)writeLocation(result.getLastLocation());}};
        try{fused.requestLocationUpdates(request,callback,getMainLooper());updateForegroundNotification();}
        catch(SecurityException e){Log.w("FamilyCircle","Location permission was revoked",e);stopSelf();}
    }
    private void writeLocation(Location location){
        lastLocation=location;
        if(locationRef==null)return;
        Map<String,Object> value=new HashMap<>();value.put("lat",location.getLatitude());value.put("lng",location.getLongitude());value.put("accuracy",(double)location.getAccuracy());value.put("updatedAt",System.currentTimeMillis());
        int percent=currentBatteryPercent();value.put("battery",Math.max(0,percent));locationRef.setValue(value);publishLowBatteryIfNeeded(percent);checkSafeArea(location);
    }
    private int currentBatteryPercent(){BatteryManager battery=(BatteryManager)getSystemService(BATTERY_SERVICE);return battery==null?-1:battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);}
    private void handleBatteryChanged(Intent intent){int level=intent.getIntExtra(BatteryManager.EXTRA_LEVEL,-1),scale=intent.getIntExtra(BatteryManager.EXTRA_SCALE,100);if(level<0||scale<=0)return;int percent=Math.round(level*100f/scale);String family=getSharedPreferences("FamilyCircle",MODE_PRIVATE).getString("familyId","");String uid=FirebaseAuth.getInstance().getUid();if(percent>7&&uid!=null&&!family.isEmpty())getSharedPreferences("FamilyCircle",MODE_PRIVATE).edit().putBoolean("lowBatterySent:"+family+":"+uid,false).apply();publishLowBatteryIfNeeded(percent);}
    private void publishLowBatteryIfNeeded(int percent){if(percent<0||percent>5||familyRef==null)return;String uid=FirebaseAuth.getInstance().getUid();if(uid==null)return;android.content.SharedPreferences prefs=getSharedPreferences("FamilyCircle",MODE_PRIVATE);String key="lowBatterySent:"+prefs.getString("familyId","")+":"+uid;if(prefs.getBoolean(key,false))return;prefs.edit().putBoolean(key,true).apply();Map<String,Object> event=new HashMap<>();event.put("memberUid",uid);event.put("memberName",prefs.getString("playerName","Family member"));event.put("battery",percent);event.put("timestamp",System.currentTimeMillis());familyRef.child("lowBatteryEvents").push().setValue(event).addOnFailureListener(error->{prefs.edit().putBoolean(key,false).apply();Log.w("FamilyCircle","Could not send low battery alert",error);});}
    private void checkSafeArea(Location location){
        if(familyRef==null)return;
        String family=getSharedPreferences("FamilyCircle",MODE_PRIVATE).getString("familyId","");
        String uid=FirebaseAuth.getInstance().getUid();
        if(uid==null||family.isEmpty())return;
        android.content.SharedPreferences prefs=getSharedPreferences("FamilyCircle",MODE_PRIVATE);boolean insideAny=false;String activeName="";
        for(SafePlace place:safePlaces.values()){
            float[] result=new float[1];Location.distanceBetween(location.getLatitude(),location.getLongitude(),place.lat,place.lng,result);
            String key="safeAreaState:"+family+":"+uid+":"+place.id;String version=String.valueOf(place.version);
            boolean sameArea=version.equals(prefs.getString(key+":version",""));boolean known=sameArea&&prefs.contains(key+":inside");boolean wasInside=known&&prefs.getBoolean(key+":inside",false);
            boolean inside=result[0] <= place.radius+(wasInside?40:0);if(inside){insideAny=true;if(activeName.isEmpty())activeName=place.name;}
            if(known&&inside==wasInside)continue;
            prefs.edit().putString(key+":version",version).putBoolean(key+":inside",inside).apply();
            if(!known)continue;
            Map<String,Object> event=new HashMap<>();event.put("memberUid",uid);event.put("memberName",prefs.getString("playerName","Family member"));event.put("areaName",place.name);event.put("type",inside?"enter":"exit");event.put("timestamp",System.currentTimeMillis());
            familyRef.child("safeAreaEvents").push().setValue(event);
        }
        activeSafeAreaName=activeName;if(insideAny!=trackingInsideSafeArea)setLocationCadence(insideAny);
    }
    private Notification notification(){
        Intent stop=new Intent(this,LocationShareService.class);stop.setAction(ACTION_STOP);
        PendingIntent stopPi=PendingIntent.getService(this,9,stop,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder=new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_menu_mylocation).setOngoing(true);
        if(pauseUntil>System.currentTimeMillis()){
            String resumesAt=new java.text.SimpleDateFormat("h:mm a",java.util.Locale.getDefault()).format(new java.util.Date(pauseUntil));
            Intent resume=new Intent(this,LocationShareService.class);resume.setAction(ACTION_RESUME);PendingIntent resumePi=PendingIntent.getService(this,10,resume,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            builder.setContentTitle("Family location sharing is paused").setContentText("No location is being collected. Resumes at "+resumesAt+".").addAction(new Notification.Action.Builder(null,"Resume now",resumePi).build());
        }else builder.setContentTitle("Family location sharing is on").setContentText(trackingInsideSafeArea?"Inside "+activeSafeAreaName+" · checking about every 4 minutes.":"Outside safe area · frequent location updates are on.");
        return builder.addAction(new Notification.Action.Builder(null,"Stop sharing",stopPi).build()).build();
    }
    private void createChannel(){if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel(CHANNEL,"Location sharing",NotificationManager.IMPORTANCE_LOW);c.setDescription("Visible while your family location sharing is active or temporarily paused.");NotificationChannel status=new NotificationChannel(STATUS_CHANNEL,"Location sharing status",NotificationManager.IMPORTANCE_DEFAULT);status.setDescription("Notifies you when a scheduled location sharing pause ends.");NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);manager.createNotificationChannel(c);manager.createNotificationChannel(status);}}
    private void updateForegroundNotification(){((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(7,notification());}
    private void pauseLocationUpdates(){if(callback!=null){fused.removeLocationUpdates(callback);callback=null;}if(locationRef!=null)locationRef.removeValue();}
    private void scheduleAutoResume(){if(autoResumeTask!=null)timerHandler.removeCallbacks(autoResumeTask);autoResumeTask=()->resumeAfterPause();timerHandler.postDelayed(autoResumeTask,Math.max(0,pauseUntil-System.currentTimeMillis()));}
    private void resumeAfterPause(){
        autoResumeTask=null;pauseUntil=0L;getSharedPreferences("FamilyCircle",MODE_PRIVATE).edit().putLong("pauseUntil",0L).apply();
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED&&checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED){notifyResumeNeedsPermission();stopNow();return;}
        begin();updateForegroundNotification();notifySharingResumed();
    }
    private void clearAndStop(){
        getSharedPreferences("FamilyCircle",MODE_PRIVATE).edit().putBoolean("sharingEnabled",false).apply();
        if(autoResumeTask!=null){timerHandler.removeCallbacks(autoResumeTask);autoResumeTask=null;}
        if(callback!=null){fused.removeLocationUpdates(callback);callback=null;}
        pauseUntil=0L;getSharedPreferences("FamilyCircle",MODE_PRIVATE).edit().putLong("pauseUntil",0L).apply();
        if(locationRef!=null)locationRef.removeValue();
        stopNow();
    }
    private void notifyStatus(String title,String message){
        Intent open=new Intent(this,MainActivity.class);PendingIntent pi=PendingIntent.getActivity(this,11,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,STATUS_CHANNEL):new Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.ic_menu_mylocation).setContentTitle(title).setContentText(message).setAutoCancel(true).setContentIntent(pi);
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(8,builder.build());
    }
    private void notifySharingResumed(){notifyStatus("Location sharing resumed","Your family can see your location again.");}
    private void notifyResumeNeedsPermission(){notifyStatus("Location sharing needs permission","Open Family Circle and allow location to resume sharing.");}
    private void stopNow(){if(Build.VERSION.SDK_INT>=24)stopForeground(STOP_FOREGROUND_REMOVE);else stopForeground(true);stopSelf();}
    @Override public void onDestroy(){if(autoResumeTask!=null)timerHandler.removeCallbacks(autoResumeTask);if(callback!=null)fused.removeLocationUpdates(callback);if(batteryReceiverRegistered){try{unregisterReceiver(batteryReceiver);}catch(IllegalArgumentException ignored){}batteryReceiverRegistered=false;}String uid=FirebaseAuth.getInstance().getUid();if(familyRef!=null&&safeAreaListener!=null&&uid!=null)familyRef.child("safeAreas").child(uid).removeEventListener(safeAreaListener);super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
