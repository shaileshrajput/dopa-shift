package com.dopashift.interception;

import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class InterceptionService_MembersInjector implements MembersInjector<InterceptionService> {
  private final Provider<InterceptionPermissionHelper> permissionHelperProvider;

  private final Provider<DeviceCapabilityChecker> deviceCapabilityCheckerProvider;

  public InterceptionService_MembersInjector(
      Provider<InterceptionPermissionHelper> permissionHelperProvider,
      Provider<DeviceCapabilityChecker> deviceCapabilityCheckerProvider) {
    this.permissionHelperProvider = permissionHelperProvider;
    this.deviceCapabilityCheckerProvider = deviceCapabilityCheckerProvider;
  }

  public static MembersInjector<InterceptionService> create(
      Provider<InterceptionPermissionHelper> permissionHelperProvider,
      Provider<DeviceCapabilityChecker> deviceCapabilityCheckerProvider) {
    return new InterceptionService_MembersInjector(permissionHelperProvider, deviceCapabilityCheckerProvider);
  }

  @Override
  public void injectMembers(InterceptionService instance) {
    injectPermissionHelper(instance, permissionHelperProvider.get());
    injectDeviceCapabilityChecker(instance, deviceCapabilityCheckerProvider.get());
  }

  @InjectedFieldSignature("com.dopashift.interception.InterceptionService.permissionHelper")
  public static void injectPermissionHelper(InterceptionService instance,
      InterceptionPermissionHelper permissionHelper) {
    instance.permissionHelper = permissionHelper;
  }

  @InjectedFieldSignature("com.dopashift.interception.InterceptionService.deviceCapabilityChecker")
  public static void injectDeviceCapabilityChecker(InterceptionService instance,
      DeviceCapabilityChecker deviceCapabilityChecker) {
    instance.deviceCapabilityChecker = deviceCapabilityChecker;
  }
}
