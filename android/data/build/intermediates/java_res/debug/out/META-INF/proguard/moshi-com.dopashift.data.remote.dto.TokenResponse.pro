-if class com.dopashift.data.remote.dto.TokenResponse
-keepnames class com.dopashift.data.remote.dto.TokenResponse
-if class com.dopashift.data.remote.dto.TokenResponse
-keep class com.dopashift.data.remote.dto.TokenResponseJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
