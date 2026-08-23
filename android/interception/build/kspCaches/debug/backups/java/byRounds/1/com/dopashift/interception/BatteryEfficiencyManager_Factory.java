package com.dopashift.interception;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava"
})
public final class BatteryEfficiencyManager_Factory implements Factory<BatteryEfficiencyManager> {
  @Override
  public BatteryEfficiencyManager get() {
    return newInstance();
  }

  public static BatteryEfficiencyManager_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static BatteryEfficiencyManager newInstance() {
    return new BatteryEfficiencyManager();
  }

  private static final class InstanceHolder {
    private static final BatteryEfficiencyManager_Factory INSTANCE = new BatteryEfficiencyManager_Factory();
  }
}
