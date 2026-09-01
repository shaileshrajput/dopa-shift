package com.dopashift.interception;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
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
public final class PermissionRevocationDetector_Factory implements Factory<PermissionRevocationDetector> {
  private final Provider<InterceptionPermissionHelper> permissionHelperProvider;

  private PermissionRevocationDetector_Factory(
      Provider<InterceptionPermissionHelper> permissionHelperProvider) {
    this.permissionHelperProvider = permissionHelperProvider;
  }

  @Override
  public PermissionRevocationDetector get() {
    return newInstance(permissionHelperProvider.get());
  }

  public static PermissionRevocationDetector_Factory create(
      Provider<InterceptionPermissionHelper> permissionHelperProvider) {
    return new PermissionRevocationDetector_Factory(permissionHelperProvider);
  }

  public static PermissionRevocationDetector newInstance(
      InterceptionPermissionHelper permissionHelper) {
    return new PermissionRevocationDetector(permissionHelper);
  }
}
