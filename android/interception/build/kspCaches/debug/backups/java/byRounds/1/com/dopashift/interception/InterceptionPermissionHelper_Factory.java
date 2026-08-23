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
public final class InterceptionPermissionHelper_Factory implements Factory<InterceptionPermissionHelper> {
  @Override
  public InterceptionPermissionHelper get() {
    return newInstance();
  }

  public static InterceptionPermissionHelper_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static InterceptionPermissionHelper newInstance() {
    return new InterceptionPermissionHelper();
  }

  private static final class InstanceHolder {
    private static final InterceptionPermissionHelper_Factory INSTANCE = new InterceptionPermissionHelper_Factory();
  }
}
