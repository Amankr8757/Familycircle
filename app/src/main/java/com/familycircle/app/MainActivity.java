package com.familycircle.app;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.net.Uri;
import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import com.familycircle.app.ui.FamilyCircleComposeView;
import com.familycircle.app.ui.FamilyUiActions;
import com.familycircle.app.ui.FamilyUiState;
import com.familycircle.app.ui.FamilyMemberUi;
import com.familycircle.app.ui.FamilyEventUi;
import com.familycircle.app.ui.SafeAreaUi;
import com.familycircle.app.ui.JoinRequestUi;
import com.familycircle.app.ui.Tab;
import com.familycircle.app.ui.LoginActions;
import com.familycircle.app.ui.LoginComposeView;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.messaging.FirebaseMessaging;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.List;
import java.text.SimpleDateFormat;
import java.util.UUID;

public class MainActivity extends ComponentActivity {
    private static final int REQ_LOCATION = 31, REQ_NOTIFICATIONS = 32, REQ_SAFE_ALERTS=33, REQ_BACKGROUND_LOCATION=34;
    private final int ink=Color.rgb(37,38,61), green=Color.rgb(86,72,232), muted=Color.rgb(116,119,145), paper=Color.rgb(246,246,252), lavender=Color.rgb(235,233,255), blue=Color.rgb(121,119,246);
    private final Handler mainHandler=new Handler(Looper.getMainLooper());
    private Runnable waitingTimeout;
    private FirebaseAuth auth;
    private DatabaseReference db, familyRef, requestRef, membershipRef;
    private ValueEventListener familyListener, requestListener, membershipListener;
    private FamilyCircleComposeView familyComposeView;
    private boolean pickingSafeArea=false;
    private DataSnapshot latestFamilySnapshot;
    private DatabaseReference safeEventsRef;
    private ChildEventListener safeEventsListener;
    private DatabaseReference profilePhotosRef;
    private ValueEventListener profilePhotosListener;
    private ActivityResultLauncher<String> profilePhotoPicker;
    private boolean updateCheckStarted=false;
    private final Map<String,String> memberPhotos=new HashMap<>();
    private final ArrayList<JoinRequestUi> composeRequests=new ArrayList<>();
    private String familyId="", playerName="", ownerUid="";
    private boolean awaitingBatteryOptimizationResult=false;
    private boolean awaitingBackgroundLocationSettingsResult=false;
    private long lastBackPressAt=0L;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getOnBackPressedDispatcher().addCallback(this,new OnBackPressedCallback(true){@Override public void handleOnBackPressed(){handleBackNavigation();}});
        profilePhotoPicker=registerForActivityResult(new ActivityResultContracts.GetContent(),uri->{if(uri!=null)saveProfilePhoto(uri);});
        getWindow().setStatusBarColor(paper); getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        try { FirebaseApp.initializeApp(this); auth=FirebaseAuth.getInstance(); db=FirebaseDatabase.getInstance(getString(R.string.firebase_database_url)).getReference(); }
        catch (Exception e) { showSetupError(); return; }
        playerName=prefs().getString("playerName",""); familyId=prefs().getString("familyId","");
        showLoading();
        if(auth.getCurrentUser()==null) signIn(); else refresh();
    }
    private android.content.SharedPreferences prefs(){return getSharedPreferences("FamilyCircle",MODE_PRIVATE);}
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(18),dp(18),dp(18),dp(22));l.setBackgroundColor(paper);return l;}
    private TextView label(String s){TextView t=text(s,12,muted);t.setTypeface(null,1);return t;}
    private TextView heading(String s){TextView t=text(s,26,ink);t.setTypeface(null,1);t.setPadding(0,dp(8),0,dp(6));return t;}
    private TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextColor(color);t.setTextSize(size);t.setLineSpacing(dp(3),1);t.setPadding(0,dp(5),0,dp(8));return t;}
    private EditText input(String hint){EditText e=new EditText(this);e.setSingleLine(true);e.setTextSize(15);e.setHint(hint);e.setTextColor(ink);e.setPadding(dp(14),0,dp(14),0);GradientDrawable bg=new GradientDrawable();bg.setColor(Color.WHITE);bg.setCornerRadius(dp(14));bg.setStroke(dp(1),Color.rgb(224,222,245));e.setBackground(bg);return e;}
    private Button button(String s,Runnable r,boolean primary){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(14);b.setMinHeight(dp(48));b.setPadding(dp(14),dp(8),dp(14),dp(8));b.setStateListAnimator(null);b.setTextColor(primary?Color.WHITE:green);GradientDrawable bg=new GradientDrawable();bg.setColor(primary?green:lavender);bg.setCornerRadius(dp(14));if(!primary)bg.setStroke(dp(1),Color.rgb(222,219,248));b.setBackground(bg);b.setOnClickListener(v->r.run());return b;}
    private GradientDrawable rounded(int color,int radius,int stroke){GradientDrawable bg=new GradientDrawable();bg.setColor(color);bg.setCornerRadius(dp(radius));if(stroke!=0)bg.setStroke(dp(1),stroke);return bg;}
    private LinearLayout softCard(){LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(16),dp(14),dp(16),dp(14));card.setBackground(rounded(Color.WHITE,18,Color.rgb(231,229,247)));return card;}
    private TextView statusChip(String value,int bgColor,int fgColor){TextView chip=text(value,12,fgColor);chip.setGravity(Gravity.CENTER);chip.setPadding(dp(10),dp(3),dp(10),dp(3));chip.setBackground(rounded(bgColor,9,0));return chip;}
    private void space(LinearLayout l,int height){l.addView(new View(this),new LinearLayout.LayoutParams(1,dp(height)));}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private void clearWaiting(){if(waitingTimeout!=null){mainHandler.removeCallbacks(waitingTimeout);waitingTimeout=null;}}
    private void showSetupError(){clearWaiting();LinearLayout l=column();l.addView(heading("Firebase setup needed"));l.addView(text("Check that app/google-services.json is present and that you replaced the Realtime Database URL placeholder in app/src/main/res/values/strings.xml. Then sync Gradle. See README.md for the setup steps.",15,muted));setContentView(l);}
    private void showWait(String heading,String detail,Runnable retry){clearWaiting();LinearLayout l=column();l.addView(label("FAMILY CIRCLE"));l.addView(heading(heading));l.addView(text(detail,15,muted));setContentView(l);waitingTimeout=()->showConnectionError(retry,null);mainHandler.postDelayed(waitingTimeout,15000);}
    private void showLoading(){showWait("Connecting…","Signing in securely with Firebase. This usually takes a moment.",this::signIn);}
    private void showConnectionError(Runnable retry,DatabaseError error){clearWaiting();LinearLayout l=column();l.addView(label("FAMILY CIRCLE"));l.addView(heading("Still unable to connect"));l.addView(text("Check emulator internet, the Firebase Realtime Database URL, and the published database rules.\n\n"+(error==null?"No response after 15 seconds.":error.getMessage()),15,muted));l.addView(button("Try again",retry,true));setContentView(l);}
    private void signIn(){showLoading();auth.signInAnonymously().addOnCompleteListener(t->{clearWaiting();if(t.isSuccessful())refresh();else showAuthError(t.getException());});}
    private void showAuthError(Exception e){clearWaiting();LinearLayout l=column();l.addView(label("FAMILY CIRCLE"));l.addView(heading("Firebase sign-in failed"));l.addView(text("Check your internet connection and make sure Firebase Authentication → Sign-in method → Anonymous is enabled.\n\n"+(e==null?"":e.getLocalizedMessage()),15,muted));l.addView(button("Try again",this::signIn,true));setContentView(l);}
    private void refresh(){if(auth==null||auth.getCurrentUser()==null)return;registerPushToken();if(!updateCheckStarted){updateCheckStarted=true;GitHubUpdateManager.checkAndDownload(this,false,null);}if(familyId.isEmpty())showWelcome();else watchFamily();}
    private void registerPushToken(){String uid=auth==null||auth.getCurrentUser()==null?null:auth.getCurrentUser().getUid();if(uid==null)return;db.child("notificationPreferences").child(uid).child("safeAreaAlerts").setValue(prefs().getBoolean("safeAlertsEnabled",false));FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token->{if(auth!=null&&auth.getCurrentUser()!=null&&uid.equals(auth.getCurrentUser().getUid()))db.child("pushTokens").child(uid).setValue(token);});}

    private void resetFamilyScreen(){if(safeEventsRef!=null&&safeEventsListener!=null)safeEventsRef.removeEventListener(safeEventsListener);safeEventsRef=null;safeEventsListener=null;if(profilePhotosRef!=null&&profilePhotosListener!=null)profilePhotosRef.removeEventListener(profilePhotosListener);profilePhotosRef=null;profilePhotosListener=null;memberPhotos.clear();if(familyComposeView!=null){familyComposeView.disposeComposition();familyComposeView=null;}pickingSafeArea=false;latestFamilySnapshot=null;composeRequests.clear();}
    private void showWelcome(){resetFamilyScreen();setContentView(new LoginComposeView(this,playerName,new LoginActions(){public void createFamily(String name){MainActivity.this.createFamily(name);}public void joinFamily(String name,String code){requestJoin(name,code);}}));}
    private void checkForAppUpdates(){GitHubUpdateManager.checkAndDownload(this,true,this::toast);}
    private String enteredName(String raw){String n=raw==null?playerName:raw.trim();if(n.isEmpty())n="Family member";if(n.length()>32)n=n.substring(0,32);playerName=n;prefs().edit().putString("playerName",n).apply();return n;}
    private void createFamily(String name){String uid=auth.getCurrentUser().getUid(),id=UUID.randomUUID().toString().replace("-","");Map<String,Object> data=new HashMap<>(),members=new HashMap<>(),self=new HashMap<>();self.put("name",enteredName(name));members.put(uid,self);data.put("ownerUid",uid);data.put("members",members);db.child("families").child(id).setValue(data).addOnSuccessListener(v->{saveFamily(id);refresh();}).addOnFailureListener(e->toast("Could not create family. Check Firebase rules."));}
    private void saveFamily(String id){familyId=id;prefs().edit().putString("familyId",id).apply();}
    private void requestJoin(String name,String code){String id=code.trim().replace(" ","").replace("-","").toLowerCase(Locale.ROOT);if(id.length()<24){toast("Enter the full family code.");return;}String uid=auth.getCurrentUser().getUid();Map<String,Object> r=new HashMap<>();r.put("name",enteredName(name));r.put("requestedAt",System.currentTimeMillis());db.child("joinRequests").child(id).child(uid).setValue(r).addOnSuccessListener(v->{saveFamily(id);toast("Request sent. The owner must approve it.");watchFamily();}).addOnFailureListener(e->toast("Request failed. Check the code and Firebase rules."));}
    private void watchFamily(){String uid=auth.getCurrentUser().getUid();showWait("Loading family…","Checking your membership with Firebase.",this::watchFamily);if(membershipRef!=null&&membershipListener!=null)membershipRef.removeEventListener(membershipListener);membershipRef=db.child("families").child(familyId).child("members").child(uid);membershipListener=new ValueEventListener(){public void onDataChange(DataSnapshot membership){clearWaiting();if(!membership.exists()){showPending();listenRequests(false);return;}watchFamilyDetails();}public void onCancelled(DatabaseError e){showConnectionError(MainActivity.this::watchFamily,e);}};membershipRef.addValueEventListener(membershipListener);}
    private void watchFamilyDetails(){showWait("Loading family…","Getting the family members and sharing status.",this::watchFamilyDetails);if(familyRef!=null&&familyListener!=null)familyRef.removeEventListener(familyListener);familyRef=db.child("families").child(familyId);familyListener=new ValueEventListener(){public void onDataChange(DataSnapshot s){clearWaiting();String uid=auth.getCurrentUser().getUid();if(!s.exists()){showWelcome();return;}ownerUid=s.child("ownerUid").getValue(String.class);prefs().edit().putString("ownerUid",ownerUid==null?"":ownerUid).apply();playerName=s.child("members").child(uid).child("name").getValue(String.class);if(playerName==null)playerName="Family member";showFamily(s);listenRequests(uid.equals(ownerUid));listenSafeAreaEvents(true);listenProfilePhotos();}public void onCancelled(DatabaseError e){showConnectionError(MainActivity.this::watchFamilyDetails,e);}};familyRef.addValueEventListener(familyListener);}
    private void showPending(){clearWaiting();resetFamilyScreen();LinearLayout l=column();l.addView(label("FAMILY CIRCLE"));l.addView(heading("Request sent"));l.addView(text("Your request is waiting for the family owner. This screen will update after approval.",15,muted));l.addView(button("Cancel request",()->leaveLocal(true),false));setContentView(l);}
    private void listenRequests(boolean owner){if(requestRef!=null&&requestListener!=null)requestRef.removeEventListener(requestListener);composeRequests.clear();if(!owner){if(familyComposeView!=null&&latestFamilySnapshot!=null)familyComposeView.update(composeState(latestFamilySnapshot));return;}requestRef=db.child("joinRequests").child(familyId);requestListener=new ValueEventListener(){public void onDataChange(DataSnapshot s){composeRequests.clear();for(DataSnapshot r:s.getChildren()){String uid=r.getKey(),n=r.child("name").getValue(String.class);if(uid==null||n==null)continue;composeRequests.add(new JoinRequestUi(uid,n));}if(familyComposeView!=null&&latestFamilySnapshot!=null)familyComposeView.update(composeState(latestFamilySnapshot));}public void onCancelled(DatabaseError e){}};requestRef.addValueEventListener(requestListener);}

    private void listenProfilePhotos(){
        if(profilePhotosRef!=null&&profilePhotosListener!=null)return;
        memberPhotos.clear();profilePhotosRef=db.child("profilePhotos").child(familyId);
        profilePhotosListener=new ValueEventListener(){
            public void onDataChange(DataSnapshot snapshot){memberPhotos.clear();for(DataSnapshot photo:snapshot.getChildren()){String uid=photo.getKey(),data=photo.getValue(String.class);if(uid!=null&&data!=null)memberPhotos.put(uid,data);}if(latestFamilySnapshot!=null&&familyComposeView!=null)familyComposeView.update(composeState(latestFamilySnapshot));}
            public void onCancelled(DatabaseError error){toast("Could not load family photos. Check the published Firebase rules.");}
        };
        profilePhotosRef.addValueEventListener(profilePhotosListener);
    }
    private void chooseProfilePhoto(){if(profilePhotoPicker!=null)profilePhotoPicker.launch("image/*");}
    private void saveProfilePhoto(Uri uri){
        toast("Preparing profile photo…");
        new Thread(()->{
            String encoded;
            try{encoded=encodeProfilePhoto(uri);}catch(Exception e){runOnUiThread(()->toast("Could not read that image. Choose another photo."));return;}
            if(encoded==null){runOnUiThread(()->toast("Choose a smaller photo and try again."));return;}
            String uid=auth.getCurrentUser().getUid(),family=familyId;
            db.child("profilePhotos").child(family).child(uid).setValue(encoded)
                    .addOnSuccessListener(v->{memberPhotos.put(uid,encoded);if(latestFamilySnapshot!=null&&familyComposeView!=null)familyComposeView.update(composeState(latestFamilySnapshot));toast("Profile photo updated across your family.");})
                    .addOnFailureListener(e->toast("Photo upload failed. Publish the profilePhotos rules and check Firebase connection."));
        },"profile-photo-encode").start();
    }
    private String encodeProfilePhoto(Uri uri)throws Exception{
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
        try(InputStream input=getContentResolver().openInputStream(uri)){if(input==null)return null;BitmapFactory.decodeStream(input,null,bounds);}
        int sample=1;while(Math.max(bounds.outWidth,bounds.outHeight)/sample>512)sample*=2;
        BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=sample;
        Bitmap decoded;try(InputStream input=getContentResolver().openInputStream(uri)){if(input==null)return null;decoded=BitmapFactory.decodeStream(input,null,options);}
        if(decoded==null)return null;
        float ratio=Math.min(1f,256f/Math.max(decoded.getWidth(),decoded.getHeight()));
        Bitmap resized=ratio<1f?Bitmap.createScaledBitmap(decoded,Math.max(1,Math.round(decoded.getWidth()*ratio)),Math.max(1,Math.round(decoded.getHeight()*ratio)),true):decoded;
        if(resized!=decoded)decoded.recycle();
        String result=null;for(int quality:new int[]{78,68,58,48,38}){ByteArrayOutputStream bytes=new ByteArrayOutputStream();resized.compress(Bitmap.CompressFormat.JPEG,quality,bytes);String candidate=Base64.encodeToString(bytes.toByteArray(),Base64.NO_WRAP);if(candidate.length()<=135000){result=candidate;break;}}
        resized.recycle();return result;
    }

    private void showFamily(DataSnapshot s){latestFamilySnapshot=s;FamilyUiState state=composeState(s);if(familyComposeView==null){familyComposeView=new FamilyCircleComposeView(this,state,new FamilyUiActions(){public void openMap(){familyComposeView.navigateTo(Tab.Map);}public void openHistory(){familyComposeView.navigateTo(Tab.History);}public void showMemberLocation(String uid){familyComposeView.showMemberOnMap(uid);}public void uploadProfilePhoto(){chooseProfilePhoto();}public void changeName(){editProfileName();}public void mapTapped(double lat,double lng){if(pickingSafeArea)showSafeAreaEditor(lat,lng);}public void toggleSharing(){if(isOwnSharing())stopSharing();else startSharing();}public void chooseSafeArea(){beginSafeAreaPick();}public void removeSafeArea(String id){MainActivity.this.removeSafeArea(id);}public void toggleAlerts(){toggleSafeAreaAlerts();}public void shareFamilyCode(){MainActivity.this.shareFamilyCode();}public void leaveFamily(){leaveLocal(false);}public void approve(String uid,String name){MainActivity.this.approve(uid,name);}public void checkUpdates(){checkForAppUpdates();}});setContentView(familyComposeView);}else{familyComposeView.update(state);if(familyComposeView.getParent()==null)setContentView(familyComposeView);}}
    private void editProfileName(){
        EditText field=input("Your name");field.setText(playerName);field.setSelection(field.getText().length());
        LinearLayout content=new LinearLayout(this);content.setPadding(dp(24),dp(8),dp(24),0);content.addView(field,new LinearLayout.LayoutParams(-1,dp(52)));
        new AlertDialog.Builder(this).setTitle("Edit profile name").setView(content).setNegativeButton("Cancel",null).setPositiveButton("Save",(dialog,which)->{
            String next=field.getText().toString().trim();
            if(next.isEmpty()||next.length()>32){toast("Name must be 1–32 characters.");return;}
            String uid=auth.getCurrentUser().getUid();
            familyRef.child("members").child(uid).child("name").setValue(next)
                    .addOnSuccessListener(v->{playerName=next;prefs().edit().putString("playerName",next).apply();toast("Profile name updated.");})
                    .addOnFailureListener(e->toast("Could not update name. Publish the member-name rule and check Firebase."));
        }).show();
    }
    private FamilyUiState composeState(DataSnapshot s){
        ArrayList<FamilyMemberUi> members=new ArrayList<>();DataSnapshot locs=s.child("locations");String currentUid=auth.getCurrentUser().getUid();
        for(DataSnapshot m:s.child("members").getChildren()){String uid=m.getKey(),name=m.child("name").getValue(String.class);if(name==null)name="Family member";DataSnapshot loc=locs.child(uid==null?"":uid);Long at=loc.child("updatedAt").getValue(Long.class),battery=loc.child("battery").getValue(Long.class);Double lat=loc.child("lat").getValue(Double.class),lng=loc.child("lng").getValue(Double.class);long age=at==null?Long.MAX_VALUE:System.currentTimeMillis()-at;boolean live=age>=0&&age<360000,hasLocation=at!=null&&lat!=null&&lng!=null;String status=live?"Sharing now":at==null?"Not sharing":"Last update "+ago(age);members.add(new FamilyMemberUi(uid==null?"":uid,name,status,live,hasLocation&&battery!=null?battery.intValue():null,hasLocation?lat:null,hasLocation?lng:null,memberPhotos.get(uid),null,hasLocation?at:null));}
        FamilyMemberUi me=null;for(FamilyMemberUi member:members)if(member.getUid().equals(currentUid)){me=member;break;}ArrayList<FamilyMemberUi> withDistance=new ArrayList<>();for(FamilyMemberUi member:members){Double distance=null;if(me!=null&&me.getLat()!=null&&me.getLng()!=null&&member.getLat()!=null&&member.getLng()!=null){float[] result=new float[1];android.location.Location.distanceBetween(me.getLat(),me.getLng(),member.getLat(),member.getLng(),result);distance=(double)result[0];}withDistance.add(new FamilyMemberUi(member.getUid(),member.getName(),member.getStatus(),member.getLive(),member.getBattery(),member.getLat(),member.getLng(),member.getPhotoBase64(),distance,member.getLocationUpdatedAt()));}members=withDistance;
        ArrayList<FamilyEventUi> events=new ArrayList<>();for(DataSnapshot e:s.child("safeAreaEvents").getChildren()){Long at=e.child("timestamp").getValue(Long.class);if(at==null)continue;String person=e.child("memberName").getValue(String.class),area=e.child("areaName").getValue(String.class),type=e.child("type").getValue(String.class);events.add(new FamilyEventUi(e.getKey()==null?"":e.getKey(),person==null?"Family member":person,area==null?"Safe area":area,type==null?"enter":type,at));}Collections.sort(events,(a,b)->Long.compare(b.getTimestamp(),a.getTimestamp()));
        ArrayList<SafeAreaUi> areas=new ArrayList<>(),myAreas=new ArrayList<>();DataSnapshot safeAreas=s.child("safeAreas");for(DataSnapshot memberNode:safeAreas.getChildren()){String areaUid=memberNode.getKey()==null?"":memberNode.getKey();DataSnapshot memberData=s.child("members").child(areaUid);String memberName=memberData.child("name").getValue(String.class);if(memberName==null)memberName="Family member";if(memberNode.hasChild("name")){SafeAreaUi area=readSafeArea(memberNode,"legacy_root",areaUid,memberName,0);if(area!=null){areas.add(area);if(areaUid.equals(currentUid))myAreas.add(area);}}else{int index=0;for(DataSnapshot safe:memberNode.getChildren()){if(!Boolean.TRUE.equals(safe.child("enabled").getValue(Boolean.class)))continue;String areaId=safe.getKey()==null?"place"+index:safe.getKey();Long colorValue=safe.child("colorIndex").getValue(Long.class);SafeAreaUi area=readSafeArea(safe,areaId,areaUid,memberName,colorValue==null?index:colorValue.intValue());if(area!=null){areas.add(area);if(areaUid.equals(currentUid))myAreas.add(area);}index++;}}}
        SafeAreaUi ownArea=myAreas.isEmpty()?null:myAreas.get(0);
        boolean owner=currentUid.equals(ownerUid);return new FamilyUiState(playerName,memberPhotos.get(currentUid),pretty(familyId),owner,isOwnSharing(),prefs().getLong("pauseUntil",0L),(int)countSharing(s),members,events,ownArea,areas,myAreas,prefs().getBoolean("safeAlertsEnabled",false),new ArrayList<>(composeRequests));
    }
    private SafeAreaUi readSafeArea(DataSnapshot safe,String id,String ownerUid,String ownerName,int colorIndex){if(!Boolean.TRUE.equals(safe.child("enabled").getValue(Boolean.class)))return null;String name=safe.child("name").getValue(String.class);Long radius=safe.child("radiusMeters").getValue(Long.class);Double lat=safe.child("lat").getValue(Double.class),lng=safe.child("lng").getValue(Double.class);return name==null||radius==null?null:new SafeAreaUi(name,radius.intValue(),lat,lng,ownerUid,ownerName,id,Math.floorMod(colorIndex,3));}
    private int countSafePlaces(DataSnapshot node){if(node.hasChild("name"))return 1;int count=0;for(DataSnapshot child:node.getChildren())if(child.hasChild("name"))count++;return count;}
    private void beginSafeAreaPick(){String uid=auth.getCurrentUser().getUid();DataSnapshot mine=latestFamilySnapshot==null?null:latestFamilySnapshot.child("safeAreas").child(uid);int count=mine==null?0:countSafePlaces(mine);if(count>=3){toast("You can add up to 3 safe areas. Remove one first.");return;}pickingSafeArea=true;if(familyComposeView!=null)familyComposeView.openMapForPicking();toast("Map par apne safe place ka center tap karein.");}
    private void showSafeAreaEditor(double lat,double lng){
        pickingSafeArea=false;boolean dark=prefs().getBoolean("darkMode",true);int fieldBg=dark?Color.rgb(39,40,59):Color.WHITE;int fieldFg=dark?Color.rgb(242,242,250):ink;int fieldStroke=dark?Color.rgb(73,72,105):Color.rgb(224,222,245);
        LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(dp(20),dp(8),dp(20),dp(4));form.setBackgroundColor(dark?Color.rgb(31,32,48):Color.WHITE);
        EditText name=input("e.g. School");name.setText("School / hostel");EditText radius=input("Radius in meters (100–3000)");radius.setInputType(InputType.TYPE_CLASS_NUMBER);radius.setText("300");
        for(EditText field:new EditText[]{name,radius}){field.setTextColor(fieldFg);field.setHintTextColor(dark?Color.rgb(167,168,190):muted);field.setBackground(rounded(fieldBg,14,fieldStroke));field.setPadding(dp(14),dp(8),dp(14),dp(8));field.setMinHeight(dp(52));}
        TextView areaLabel=label("AREA NAME"),radiusLabel=label("CIRCLE RADIUS");if(dark){areaLabel.setTextColor(Color.rgb(193,192,220));radiusLabel.setTextColor(Color.rgb(193,192,220));}
        form.addView(areaLabel);form.addView(name);space(form,10);form.addView(radiusLabel);form.addView(radius);
        int dialogTheme=dark?android.R.style.Theme_Material_Dialog_Alert:android.R.style.Theme_Material_Light_Dialog_Alert;
        AlertDialog dialog=new AlertDialog.Builder(this,dialogTheme).setTitle("Set my safe area")
            .setMessage(String.format(Locale.US,"Map center: %.5f, %.5f",lat,lng)).setView(form)
            .setNegativeButton("Cancel",(d,w)->{if(familyComposeView!=null)familyComposeView.navigateTo(Tab.Map);})
            .setPositiveButton("Save circle",(d,w)->{String title=name.getText().toString().trim();int meters;try{meters=Integer.parseInt(radius.getText().toString().trim());}catch(Exception e){meters=0;}if(title.isEmpty()||title.length()>32||meters<100||meters>3000){toast("Enter a name and radius from 100 to 3000 meters.");return;}saveSafePlace(title,lat,lng,meters);}).create();
        dialog.show();dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(dark?Color.rgb(175,164,255):green);dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(dark?Color.rgb(193,192,220):muted);if(dialog.getWindow()!=null){dialog.getWindow().setBackgroundDrawable(rounded(dark?Color.rgb(31,32,48):Color.WHITE,24,dark?Color.rgb(58,59,82):Color.rgb(232,230,247)));dialog.getWindow().setElevation(dp(20));}
    }
    private void saveSafePlace(String title,double lat,double lng,int radiusMeters){
        String uid=auth.getCurrentUser().getUid();DatabaseReference placesRef=db.child("families").child(familyId).child("safeAreas").child(uid);
        placesRef.get().addOnSuccessListener(snapshot->{
            int existing=countSafePlaces(snapshot);if(existing>=3){toast("You can add up to 3 safe areas. Remove one first.");return;}
            String placeId=placesRef.push().getKey();if(placeId==null)placeId="place"+System.currentTimeMillis();
            int colorIndex=nextSafeAreaColor(snapshot);Map<String,Object> area=new HashMap<>();area.put("name",title);area.put("lat",lat);area.put("lng",lng);area.put("radiusMeters",radiusMeters);area.put("enabled",true);area.put("updatedAt",System.currentTimeMillis());area.put("updatedBy",uid);area.put("colorIndex",colorIndex);
            Map<String,Object> updates=new HashMap<>();
            if(snapshot.hasChild("name")){
                Map<String,Object> old=new HashMap<>();old.put("name",snapshot.child("name").getValue(String.class));old.put("lat",snapshot.child("lat").getValue());old.put("lng",snapshot.child("lng").getValue());old.put("radiusMeters",snapshot.child("radiusMeters").getValue());old.put("enabled",snapshot.child("enabled").getValue(Boolean.class));old.put("updatedAt",snapshot.child("updatedAt").getValue());old.put("updatedBy",uid);old.put("colorIndex",0);
                updates.put("legacy",old);for(String field:new String[]{"name","lat","lng","radiusMeters","enabled","updatedAt","updatedBy","colorIndex"})updates.put(field,null);
            }
            updates.put(placeId,area);
            placesRef.updateChildren(updates).addOnSuccessListener(v->toast("Safe area saved. Each place has its own map color."))
                    .addOnFailureListener(e->toast("Could not save safe area. Publish the updated Firebase rules first."));
        }).addOnFailureListener(e->toast("Could not load your saved safe areas."));
    }
    private int nextSafeAreaColor(DataSnapshot node){boolean[] used=new boolean[3];if(node.hasChild("name"))used[0]=true;else for(DataSnapshot child:node.getChildren()){Long index=child.child("colorIndex").getValue(Long.class);if(index!=null&&index>=0&&index<3)used[index.intValue()]=true;}for(int i=0;i<used.length;i++)if(!used[i])return i;return 0;}
    private void handleBackNavigation(){
        if(familyComposeView!=null&&familyComposeView.getCurrentTab()!=Tab.Home){familyComposeView.navigateTo(Tab.Home);lastBackPressAt=0L;return;}
        long now=System.currentTimeMillis();if(now-lastBackPressAt>2500L){lastBackPressAt=now;toast("Press back again to exit.");return;}
        lastBackPressAt=0L;boolean dark=prefs().getBoolean("darkMode",true);int theme=dark?android.R.style.Theme_Material_Dialog_Alert:android.R.style.Theme_Material_Light_Dialog_Alert;
        new AlertDialog.Builder(this,theme).setTitle("Exit Family Circle?")
            .setMessage(isOwnSharing()?"The app will close. Location sharing will keep running with its notification until you pause or stop it.":"Do you want to close Family Circle?")
            .setNegativeButton("Stay",null).setPositiveButton("Exit",(d,w)->finish()).show();
    }
    private void removeSafeArea(String id){String uid=auth.getCurrentUser().getUid();String label="this safe area";for(SafeAreaUi area:composeState(latestFamilySnapshot).getMySafeAreas())if(area.getId().equals(id)){label=area.getName();break;}final String areaName=label;new AlertDialog.Builder(this).setTitle("Remove "+areaName+"?").setMessage("Entry and exit alerts for this place will stop.").setNegativeButton("Cancel",null).setPositiveButton("Remove",(d,w)->{DatabaseReference ownRef=db.child("families").child(familyId).child("safeAreas").child(uid);DatabaseReference removeRef="legacy_root".equals(id)?ownRef:ownRef.child(id);removeRef.removeValue().addOnSuccessListener(v->toast(areaName+" removed.")).addOnFailureListener(e->toast("Could not remove safe area. Check Firebase rules."));}).show();}
    private void setSafeAreaAlertsEnabled(boolean enabled){prefs().edit().putBoolean("safeAlertsEnabled",enabled).apply();if(auth!=null&&auth.getCurrentUser()!=null)db.child("notificationPreferences").child(auth.getCurrentUser().getUid()).child("safeAreaAlerts").setValue(enabled).addOnFailureListener(e->toast("Could not sync alert setting. Check Firebase rules."));if(latestFamilySnapshot!=null&&familyComposeView!=null)familyComposeView.update(composeState(latestFamilySnapshot));}
    private void toggleSafeAreaAlerts(){if(prefs().getBoolean("safeAlertsEnabled",false)){setSafeAreaAlertsEnabled(false);return;}if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},REQ_SAFE_ALERTS);return;}setSafeAreaAlertsEnabled(true);toast("Family safe-area alerts enabled on this phone.");}
    private void listenSafeAreaEvents(boolean joinedMember){if(safeEventsRef!=null&&safeEventsListener!=null)return;if(!joinedMember)return;safeEventsRef=db.child("families").child(familyId).child("safeAreaEvents");safeEventsListener=new ChildEventListener(){public void onChildAdded(DataSnapshot s,String previous){Long at=s.child("timestamp").getValue(Long.class);if(at==null)return;String seenKey="safeAreaEventSeen:"+s.getKey();if(prefs().getBoolean(seenKey,false))return;prefs().edit().putBoolean(seenKey,true).apply();if(System.currentTimeMillis()-at>120000||!prefs().getBoolean("safeAlertsEnabled",false)||Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;String person=s.child("memberName").getValue(String.class),area=s.child("areaName").getValue(String.class),type=s.child("type").getValue(String.class);if(person==null)person="A family member";if(area==null)area="the safe area";showSafeAreaNotification(s.getKey(),person,area,"enter".equals(type));}public void onChildChanged(DataSnapshot s,String previous){}public void onChildRemoved(DataSnapshot s){}public void onChildMoved(DataSnapshot s,String previous){}public void onCancelled(DatabaseError e){}};safeEventsRef.addChildEventListener(safeEventsListener);}
    private void showSafeAreaNotification(String key,String person,String area,boolean entered){String channel="safe_area_alerts_sound_v2";NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel(channel,"Safe area alerts",NotificationManager.IMPORTANCE_HIGH);c.setDescription("Sound alerts when a family member enters or leaves a saved safe area.");c.setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());c.enableVibration(true);manager.createNotificationChannel(c);}Intent open=new Intent(this,MainActivity.class);PendingIntent pending=PendingIntent.getActivity(this,(int)System.currentTimeMillis(),open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,channel):new Notification.Builder(this).setDefaults(Notification.DEFAULT_SOUND|Notification.DEFAULT_VIBRATE).setPriority(Notification.PRIORITY_HIGH);b.setSmallIcon(android.R.drawable.ic_dialog_map).setContentTitle(entered?"Arrived at "+area:"Left "+area).setContentText(person+(entered?" entered ":" left ")+area).setAutoCancel(true).setContentIntent(pending);manager.notify(key==null?(int)System.currentTimeMillis():key.hashCode(),b.build());}
    private long countSharing(DataSnapshot s){if(s==null)return 0;long count=0,now=System.currentTimeMillis();for(DataSnapshot m:s.child("members").getChildren()){Long at=s.child("locations").child(m.getKey()).child("updatedAt").getValue(Long.class);if(at!=null&&now-at<360000)count++;}return count;}
    private boolean isOwnSharing(){if(latestFamilySnapshot==null||auth==null||auth.getCurrentUser()==null)return false;Long at=latestFamilySnapshot.child("locations").child(auth.getCurrentUser().getUid()).child("updatedAt").getValue(Long.class);return at!=null&&System.currentTimeMillis()-at<360000;}
    private void shareFamilyCode(){Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_TEXT,"Join our Family Circle. Enter this code in the app: "+pretty(familyId));startActivity(Intent.createChooser(i,"Share family code"));}
    private String ago(long ms){if(ms==Long.MAX_VALUE)return "no location shared";long m=Math.max(1,ms/60000);return m<60?m+" min ago":(m/60)+" hr ago";}
    private String pretty(String id){return id.replaceAll("(.{4})(?!$)","$1-");}
    private void approve(String uid,String name){Map<String,Object> updates=new HashMap<>(),member=new HashMap<>();member.put("name",name);updates.put("families/"+familyId+"/members/"+uid,member);updates.put("joinRequests/"+familyId+"/"+uid,null);db.updateChildren(updates).addOnSuccessListener(v->toast(name+" approved")).addOnFailureListener(e->toast("Approval failed. Check database rules."));}

    private boolean sharingPaused(){long until=prefs().getLong("pauseUntil",0L);return until>System.currentTimeMillis();}
    private void startSharing(){
        if(Build.VERSION.SDK_INT>=23&&!getSystemService(android.os.PowerManager.class).isIgnoringBatteryOptimizations(getPackageName())&&!prefs().getBoolean("batteryOptimizationPromptDismissed",false)){
            new AlertDialog.Builder(this).setTitle("Allow background location")
                    .setMessage("For more reliable family location updates after you leave the app, Android can exempt Family Circle from battery optimization. Location sharing will still show its ongoing notification, and you can stop it anytime.")
                    .setNegativeButton("Not now",null)
                    .setNeutralButton("Continue anyway",(d,w)->{prefs().edit().putBoolean("batteryOptimizationPromptDismissed",true).apply();startSharingWithPermissions();})
                    .setPositiveButton("Allow",(d,w)->requestBatteryOptimizationExemption()).show();
            return;
        }
        startSharingWithPermissions();
    }
    private void requestBatteryOptimizationExemption(){
        try{
            awaitingBatteryOptimizationResult=true;
            Intent request=new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,Uri.parse("package:"+getPackageName()));
            startActivity(request);
        }catch(Exception e){
            awaitingBatteryOptimizationResult=false;
            try{startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));}
            catch(Exception ignored){toast("Open Android Settings → Apps → Family Circle → Battery to allow background activity.");}
        }
    }
    private void startSharingWithPermissions(){
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED&&checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},REQ_LOCATION);return;}
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},REQ_NOTIFICATIONS);return;}
        continueSharingPermissionFlow();
    }
    private void continueSharingPermissionFlow(){
        if(Build.VERSION.SDK_INT>=30&&checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            new AlertDialog.Builder(this).setTitle("Allow location all the time")
                    .setMessage("To resume sharing after a phone restart, Android needs Family Circle location access set to Allow all the time. You can still share without it, but automatic reboot resume will not work.")
                    .setNegativeButton("Continue without it",(d,w)->{toast("Sharing will start, but reboot auto-resume needs Allow all the time in App permissions.");startServiceNow();})
                    .setPositiveButton("Open app settings",(d,w)->{awaitingBackgroundLocationSettingsResult=true;startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));}).show();
            return;
        }
        if(Build.VERSION.SDK_INT==29&&checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},REQ_BACKGROUND_LOCATION);return;}
        startServiceNow();
    }
    @Override protected void onResume(){super.onResume();if(awaitingBatteryOptimizationResult){awaitingBatteryOptimizationResult=false;boolean allowed=Build.VERSION.SDK_INT<23||getSystemService(android.os.PowerManager.class).isIgnoringBatteryOptimizations(getPackageName());prefs().edit().putBoolean("batteryOptimizationPromptDismissed",!allowed).apply();startSharingWithPermissions();}if(awaitingBackgroundLocationSettingsResult){awaitingBackgroundLocationSettingsResult=false;if(checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)==PackageManager.PERMISSION_GRANTED)startServiceNow();else{toast("Allow all the time to resume sharing after reboot. Starting sharing now; reboot resume will wait for that permission.");startServiceNow();}}}
    private void startServiceNow(){
        Intent i=new Intent(this,LocationShareService.class);i.setAction(sharingPaused()?LocationShareService.ACTION_RESUME:LocationShareService.ACTION_START);
        if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);
        toast("Location sharing is on. It continues until you stop it or schedule a pause.");
    }
    private void stopSharing(){choosePauseDuration();}
    private void choosePauseDuration(){
        LinearLayout pickerRow=new LinearLayout(this);pickerRow.setGravity(Gravity.CENTER);NumberPicker hours=new NumberPicker(this),minutes=new NumberPicker(this);hours.setMinValue(0);hours.setMaxValue(2);hours.setValue(1);minutes.setMinValue(0);minutes.setMaxValue(59);minutes.setValue(0);pickerRow.addView(hours,new LinearLayout.LayoutParams(dp(110),-2));pickerRow.addView(minutes,new LinearLayout.LayoutParams(dp(110),-2));
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(24),dp(8),dp(24),0);TextView labels=text("HOURS                               MINUTES",12,muted);labels.setGravity(Gravity.CENTER);content.addView(labels);content.addView(pickerRow);
        new AlertDialog.Builder(this).setTitle("Pause sharing for how long?").setView(content).setNegativeButton("Cancel",null).setPositiveButton("Pause",(d,w)->{int totalMinutes=hours.getValue()*60+minutes.getValue();if(totalMinutes<5||totalMinutes>120){toast("Choose a pause between 5 minutes and 2 hours.");return;}pauseSharingUntil(System.currentTimeMillis()+totalMinutes*60L*1000L);}).show();
    }
    private void pauseSharingUntil(long until){if(until<=System.currentTimeMillis()){toast("Choose a future time.");return;}Intent i=new Intent(this,LocationShareService.class);i.setAction(LocationShareService.ACTION_PAUSE);i.putExtra(LocationShareService.EXTRA_PAUSE_UNTIL,until);if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);String time=android.text.format.DateFormat.getTimeFormat(this).format(until);toast("Sharing paused until "+time+". It will resume and notify you then.");}
    private void stopSharingImmediately(){if(!familyId.isEmpty()&&auth.getCurrentUser()!=null)db.child("families").child(familyId).child("locations").child(auth.getCurrentUser().getUid()).removeValue();Intent i=new Intent(this,LocationShareService.class);i.setAction(LocationShareService.ACTION_STOP);startService(i);}
    @Override public void onRequestPermissionsResult(int code,String[] p,int[] g){super.onRequestPermissionsResult(code,p,g);if(code==REQ_LOCATION){boolean ok=false;for(int x:g)if(x==PackageManager.PERMISSION_GRANTED)ok=true;if(ok){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},REQ_NOTIFICATIONS);else continueSharingPermissionFlow();}else toast("Location permission is needed to share your location.");}else if(code==REQ_NOTIFICATIONS){if(g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)continueSharingPermissionFlow();else toast("Allow notifications to start timed sharing and receive its end alert.");}else if(code==REQ_BACKGROUND_LOCATION){if(g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)startServiceNow();else{toast("Allow all the time to resume sharing after reboot. Starting sharing now without automatic reboot resume.");startServiceNow();}}else if(code==REQ_SAFE_ALERTS){boolean ok=g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED;setSafeAreaAlertsEnabled(ok);if(ok)toast("Family safe-area alerts enabled on this phone.");else toast("Allow notifications to receive safe area alerts.");}}
    private void leaveLocal(boolean cancel){String uid=auth.getCurrentUser().getUid();if(cancel)db.child("joinRequests").child(familyId).child(uid).removeValue();else{stopSharingImmediately();db.child("families").child(familyId).child("members").child(uid).removeValue();}prefs().edit().remove("familyId").remove("ownerUid").apply();if(familyRef!=null&&familyListener!=null)familyRef.removeEventListener(familyListener);if(requestRef!=null&&requestListener!=null)requestRef.removeEventListener(requestListener);if(membershipRef!=null&&membershipListener!=null)membershipRef.removeEventListener(membershipListener);if(safeEventsRef!=null&&safeEventsListener!=null)safeEventsRef.removeEventListener(safeEventsListener);if(profilePhotosRef!=null&&profilePhotosListener!=null)profilePhotosRef.removeEventListener(profilePhotosListener);familyId="";ownerUid="";familyRef=null;requestRef=null;membershipRef=null;safeEventsRef=null;safeEventsListener=null;profilePhotosRef=null;profilePhotosListener=null;memberPhotos.clear();refresh();}
    @Override protected void onDestroy(){if(familyRef!=null&&familyListener!=null)familyRef.removeEventListener(familyListener);if(requestRef!=null&&requestListener!=null)requestRef.removeEventListener(requestListener);if(membershipRef!=null&&membershipListener!=null)membershipRef.removeEventListener(membershipListener);if(safeEventsRef!=null&&safeEventsListener!=null)safeEventsRef.removeEventListener(safeEventsListener);if(profilePhotosRef!=null&&profilePhotosListener!=null)profilePhotosRef.removeEventListener(profilePhotosListener);if(familyComposeView!=null)familyComposeView.disposeComposition();super.onDestroy();}
}
