-if class com.dopashift.data.remote.dto.GoalResponse
-keepnames class com.dopashift.data.remote.dto.GoalResponse
-if class com.dopashift.data.remote.dto.GoalResponse
-keep class com.dopashift.data.remote.dto.GoalResponseJsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
