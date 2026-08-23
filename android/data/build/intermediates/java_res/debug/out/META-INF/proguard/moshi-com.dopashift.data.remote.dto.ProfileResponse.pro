-if class com.dopashift.data.remote.dto.ProfileResponse
-keepnames class com.dopashift.data.remote.dto.ProfileResponse
-if class com.dopashift.data.remote.dto.ProfileResponse
-keep class com.dopashift.data.remote.dto.ProfileResponseJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
