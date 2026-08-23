-if class com.dopashift.data.remote.dto.RefreshTokenRequest
-keepnames class com.dopashift.data.remote.dto.RefreshTokenRequest
-if class com.dopashift.data.remote.dto.RefreshTokenRequest
-keep class com.dopashift.data.remote.dto.RefreshTokenRequestJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
