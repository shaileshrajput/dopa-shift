-if class com.dopashift.data.remote.dto.TokenRequest
-keepnames class com.dopashift.data.remote.dto.TokenRequest
-if class com.dopashift.data.remote.dto.TokenRequest
-keep class com.dopashift.data.remote.dto.TokenRequestJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-if class com.dopashift.data.remote.dto.TokenRequest
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-if class com.dopashift.data.remote.dto.TokenRequest
-keepclassmembers class com.dopashift.data.remote.dto.TokenRequest {
    public synthetic <init>(java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,int,kotlin.jvm.internal.DefaultConstructorMarker);
}
