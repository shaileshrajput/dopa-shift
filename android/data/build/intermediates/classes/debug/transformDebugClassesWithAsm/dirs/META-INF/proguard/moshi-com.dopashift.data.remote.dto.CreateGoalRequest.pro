-if class com.dopashift.data.remote.dto.CreateGoalRequest
-keepnames class com.dopashift.data.remote.dto.CreateGoalRequest
-if class com.dopashift.data.remote.dto.CreateGoalRequest
-keep class com.dopashift.data.remote.dto.CreateGoalRequestJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
