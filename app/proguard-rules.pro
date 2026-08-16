# Playback panel reflection.
#
# These members are looked up by name at runtime, so R8 must not rename or strip them. Auxio, which
# these views are ported from, sidesteps this by disabling obfuscation outright; we obfuscate, so
# each reflected member needs an explicit rule or the panel crashes on first inflate in release.

# RecyclerView.mTouchSlop, raised by ViewPager2.dampen() so vertical sheet drags are not eaten as
# horizontal cover swipes. Best-effort only: R8 currently inlines this field away regardless, so
# dampen() is written to no-op when the lookup misses.
-keepclassmembers class androidx.recyclerview.widget.RecyclerView {
    int mTouchSlop;
}

# AppCompatButton.mBackgroundTintHelper, nulled by RippleFixMaterialButton to remove the double
# ripple AppCompat 1.5+ draws on MaterialButton.
-keepclassmembers class androidx.appcompat.widget.AppCompatButton {
    *** mBackgroundTintHelper;
}

# Used reflectively by ScaledPlaybackButton to undo MaterialButtonGroup's temporary width
# mutations before applying playback-specific scaling.
-keepclassmembers class com.google.android.material.button.MaterialButton {
    void recoverOriginalLayoutParams();
}

# Custom serializable
-keepclassmembers class * implements java.io.Serializable {
  static final long serialVersionUID;
  java.lang.Object writeReplace();
  java.lang.Object readResolve();
  private static final java.io.ObjectStreamField[] serialPersistentFields;
  private <fields>;
  public <fields>;
}

# Gson uses generic type information stored in a class file when working with
# fields. Proguard removes such information by default, keep it.
-keepattributes Signature
# This is also needed for R8 in compat mode since multiple optimizations will
# remove the generic signature such as class merging and argument removal.
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# EventBus
-keepattributes *Annotation*
-keepclassmembers class ** {
    @org.greenrobot.eventbus.Subscribe <methods>;
}
-keep enum org.greenrobot.eventbus.ThreadMode { *; }

# Jaudiotagger
-keep public class org.jaudiotagger.** { public protected *; }
-keepnames class org.jaudiotagger.**
-dontwarn org.jaudiotagger.**
