-if class com.dopashift.data.remote.dto.LogoutRequest
-keepnames class com.dopashift.data.remote.dto.LogoutRequest
-if class com.dopashift.data.remote.dto.LogoutRequest
-keep class com.dopashift.data.remote.dto.LogoutRequestJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
