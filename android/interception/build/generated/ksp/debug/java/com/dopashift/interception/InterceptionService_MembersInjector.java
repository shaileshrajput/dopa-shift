package com.dopashift.interception;

import com.dopashift.domain.repository.InterceptActionAuditRepository;
import com.dopashift.interception.reminder.InterceptionTodoReminderNotifier;
import com.dopashift.interception.reminder.LimitReachedNotifier;
import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import java.time.Clock;
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

  private final Provider<RepetitiveSessionTracker> repetitiveSessionTrackerProvider;

  private final Provider<InterceptionTodoReminderNotifier> todoReminderNotifierProvider;

  private final Provider<FocusExtensionManager> focusExtensionManagerProvider;

  private final Provider<DeferredInterceptCoordinator> deferredInterceptCoordinatorProvider;

  private final Provider<LimitReachedNotifier> limitReachedNotifierProvider;

  private final Provider<InterceptActionAuditRepository> interceptActionAuditRepositoryProvider;

  private final Provider<Clock> clockProvider;

  private InterceptionService_MembersInjector(
      Provider<InterceptionPermissionHelper> permissionHelperProvider,
      Provider<DeviceCapabilityChecker> deviceCapabilityCheckerProvider,
      Provider<AllowanceTracker> allowanceTrackerProvider,
      Provider<RepetitiveSessionTracker> repetitiveSessionTrackerProvider,
      Provider<InterceptionTodoReminderNotifier> todoReminderNotifierProvider,
      Provider<FocusExtensionManager> focusExtensionManagerProvider,
      Provider<DeferredInterceptCoordinator> deferredInterceptCoordinatorProvider,
      Provider<LimitReachedNotifier> limitReachedNotifierProvider,
      Provider<InterceptActionAuditRepository> interceptActionAuditRepositoryProvider,
      Provider<Clock> clockProvider) {
    this.permissionHelperProvider = permissionHelperProvider;
    this.deviceCapabilityCheckerProvider = deviceCapabilityCheckerProvider;
    this.allowanceTrackerProvider = allowanceTrackerProvider;
    this.repetitiveSessionTrackerProvider = repetitiveSessionTrackerProvider;
    this.todoReminderNotifierProvider = todoReminderNotifierProvider;
    this.focusExtensionManagerProvider = focusExtensionManagerProvider;
    this.deferredInterceptCoordinatorProvider = deferredInterceptCoordinatorProvider;
    this.limitReachedNotifierProvider = limitReachedNotifierProvider;
    this.interceptActionAuditRepositoryProvider = interceptActionAuditRepositoryProvider;
    this.clockProvider = clockProvider;
  }

  @Override
  public void injectMembers(InterceptionService instance) {
    injectPermissionHelper(instance, permissionHelperProvider.get());
    injectDeviceCapabilityChecker(instance, deviceCapabilityCheckerProvider.get());
    injectAllowanceTracker(instance, allowanceTrackerProvider.get());
    injectRepetitiveSessionTracker(instance, repetitiveSessionTrackerProvider.get());
    injectTodoReminderNotifier(instance, todoReminderNotifierProvider.get());
    injectFocusExtensionManager(instance, focusExtensionManagerProvider.get());
    injectDeferredInterceptCoordinator(instance, deferredInterceptCoordinatorProvider.get());
    injectLimitReachedNotifier(instance, limitReachedNotifierProvider.get());
    injectInterceptActionAuditRepository(instance, interceptActionAuditRepositoryProvider.get());
    injectClock(instance, clockProvider.get());
  }

  public static MembersInjector<InterceptionService> create(
      Provider<InterceptionPermissionHelper> permissionHelperProvider,
      Provider<DeviceCapabilityChecker> deviceCapabilityCheckerProvider,
      Provider<AllowanceTracker> allowanceTrackerProvider,
      Provider<RepetitiveSessionTracker> repetitiveSessionTrackerProvider,
      Provider<InterceptionTodoReminderNotifier> todoReminderNotifierProvider,
      Provider<FocusExtensionManager> focusExtensionManagerProvider,
      Provider<DeferredInterceptCoordinator> deferredInterceptCoordinatorProvider,
      Provider<LimitReachedNotifier> limitReachedNotifierProvider,
      Provider<InterceptActionAuditRepository> interceptActionAuditRepositoryProvider,
      Provider<Clock> clockProvider) {
    return new InterceptionService_MembersInjector(permissionHelperProvider, deviceCapabilityCheckerProvider, allowanceTrackerProvider, repetitiveSessionTrackerProvider, todoReminderNotifierProvider, focusExtensionManagerProvider, deferredInterceptCoordinatorProvider, limitReachedNotifierProvider, interceptActionAuditRepositoryProvider, clockProvider);
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

  @InjectedFieldSignature("com.dopashift.interception.InterceptionService.repetitiveSessionTracker")
  public static void injectRepetitiveSessionTracker(InterceptionService instance,
      RepetitiveSessionTracker repetitiveSessionTracker) {
    instance.repetitiveSessionTracker = repetitiveSessionTracker;
  }

  @InjectedFieldSignature("com.dopashift.interception.InterceptionService.todoReminderNotifier")
  public static void injectTodoReminderNotifier(InterceptionService instance,
      InterceptionTodoReminderNotifier todoReminderNotifier) {
    instance.todoReminderNotifier = todoReminderNotifier;
  }

  @InjectedFieldSignature("com.dopashift.interception.InterceptionService.focusExtensionManager")
  public static void injectFocusExtensionManager(InterceptionService instance,
      FocusExtensionManager focusExtensionManager) {
    instance.focusExtensionManager = focusExtensionManager;
  }

  @InjectedFieldSignature("com.dopashift.interception.InterceptionService.deferredInterceptCoordinator")
  public static void injectDeferredInterceptCoordinator(InterceptionService instance,
      DeferredInterceptCoordinator deferredInterceptCoordinator) {
    instance.deferredInterceptCoordinator = deferredInterceptCoordinator;
  }

  @InjectedFieldSignature("com.dopashift.interception.InterceptionService.limitReachedNotifier")
  public static void injectLimitReachedNotifier(InterceptionService instance,
      LimitReachedNotifier limitReachedNotifier) {
    instance.limitReachedNotifier = limitReachedNotifier;
  }

  @InjectedFieldSignature("com.dopashift.interception.InterceptionService.interceptActionAuditRepository")
  public static void injectInterceptActionAuditRepository(InterceptionService instance,
      InterceptActionAuditRepository interceptActionAuditRepository) {
    instance.interceptActionAuditRepository = interceptActionAuditRepository;
  }

  @InjectedFieldSignature("com.dopashift.interception.InterceptionService.clock")
  public static void injectClock(InterceptionService instance, Clock clock) {
    instance.clock = clock;
  }
}
