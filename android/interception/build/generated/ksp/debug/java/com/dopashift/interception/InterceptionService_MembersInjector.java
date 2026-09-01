package com.dopashift.interception;

import com.dopashift.interception.reminder.InterceptionTodoReminderNotifier;
import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;

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
public final class InterceptionService_MembersInjector implements MembersInjector<InterceptionService> {
  private final Provider<InterceptionPermissionHelper> permissionHelperProvider;

  private final Provider<DeviceCapabilityChecker> deviceCapabilityCheckerProvider;

  private final Provider<AllowanceTracker> allowanceTrackerProvider;

  private final Provider<InterceptionTodoReminderNotifier> todoReminderNotifierProvider;

  private InterceptionService_MembersInjector(
      Provider<InterceptionPermissionHelper> permissionHelperProvider,
      Provider<DeviceCapabilityChecker> deviceCapabilityCheckerProvider,
      Provider<AllowanceTracker> allowanceTrackerProvider,
      Provider<InterceptionTodoReminderNotifier> todoReminderNotifierProvider) {
    this.permissionHelperProvider = permissionHelperProvider;
    this.deviceCapabilityCheckerProvider = deviceCapabilityCheckerProvider;
    this.allowanceTrackerProvider = allowanceTrackerProvider;
    this.todoReminderNotifierProvider = todoReminderNotifierProvider;
  }

  @Override
  public void injectMembers(InterceptionService instance) {
    injectPermissionHelper(instance, permissionHelperProvider.get());
    injectDeviceCapabilityChecker(instance, deviceCapabilityCheckerProvider.get());
    injectAllowanceTracker(instance, allowanceTrackerProvider.get());
    injectTodoReminderNotifier(instance, todoReminderNotifierProvider.get());
  }

  public static MembersInjector<InterceptionService> create(
      Provider<InterceptionPermissionHelper> permissionHelperProvider,
      Provider<DeviceCapabilityChecker> deviceCapabilityCheckerProvider,
      Provider<AllowanceTracker> allowanceTrackerProvider,
      Provider<InterceptionTodoReminderNotifier> todoReminderNotifierProvider) {
    return new InterceptionService_MembersInjector(permissionHelperProvider, deviceCapabilityCheckerProvider, allowanceTrackerProvider, todoReminderNotifierProvider);
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

  @InjectedFieldSignature("com.dopashift.interception.InterceptionService.allowanceTracker")
  public static void injectAllowanceTracker(InterceptionService instance,
      AllowanceTracker allowanceTracker) {
    instance.allowanceTracker = allowanceTracker;
  }

  @InjectedFieldSignature("com.dopashift.interception.InterceptionService.todoReminderNotifier")
  public static void injectTodoReminderNotifier(InterceptionService instance,
      InterceptionTodoReminderNotifier todoReminderNotifier) {
    instance.todoReminderNotifier = todoReminderNotifier;
  }
}
