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
    "KotlinInternalInJava",
    "cast",
    "deprecation",
    "nullness:initialization.field.uninitialized"
})
public final class DeviceCapabilityChecker_Factory implements Factory<DeviceCapabilityChecker> {
  @Override
  public DeviceCapabilityChecker get() {
    return newInstance();
  }

  public static DeviceCapabilityChecker_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static DeviceCapabilityChecker newInstance() {
    return new DeviceCapabilityChecker();
  }

  private static final class InstanceHolder {
    static final DeviceCapabilityChecker_Factory INSTANCE = new DeviceCapabilityChecker_Factory();
  }
}
