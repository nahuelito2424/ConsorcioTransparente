# Add project specific ProGuard rules here.
# Firestore model classes need their no-arg constructor and fields kept for reflection-based (de)serialization.
-keep class com.consorciotransparente.app.data.model.** { *; }
