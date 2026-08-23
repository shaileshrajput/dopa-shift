-if class com.dopashift.data.remote.dto.ChangePasswordRequest
-keepnames class com.dopashift.data.remote.dto.ChangePasswordRequest
-if class com.dopashift.data.remote.dto.ChangePasswordRequest
-keep class com.dopashift.data.remote.dto.ChangePasswordRequestJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
