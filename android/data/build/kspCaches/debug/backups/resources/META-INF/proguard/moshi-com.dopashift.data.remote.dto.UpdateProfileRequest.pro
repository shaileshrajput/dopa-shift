-if class com.dopashift.data.remote.dto.UpdateProfileRequest
-keepnames class com.dopashift.data.remote.dto.UpdateProfileRequest
-if class com.dopashift.data.remote.dto.UpdateProfileRequest
-keep class com.dopashift.data.remote.dto.UpdateProfileRequestJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.dopashift.data.remote.dto.UpdateProfileRequest
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.dopashift.data.remote.dto.UpdateProfileRequest
-keepclassmembers class com.dopashift.data.remote.dto.UpdateProfileRequest {
    public synthetic <init>(java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
