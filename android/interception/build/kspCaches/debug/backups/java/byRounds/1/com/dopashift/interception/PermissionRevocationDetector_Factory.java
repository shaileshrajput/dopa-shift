package com.dopashift.interception;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class PermissionRevocationDetector_Factory implements Factory<PermissionRevocationDetector> {
  private final Provider<InterceptionPermissionHelper> permissionHelperProvider;

  public PermissionRevocationDetector_Factory(
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
