package com.dopashift.app;

import android.app.Activity;
import android.app.Service;
import android.view.View;
import androidx.datastore.core.DataStore;
import androidx.datastore.preferences.core.Preferences;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import com.dopashift.data.di.DataModule_ProvideChangeLogDaoFactory;
import com.dopashift.data.di.DataModule_ProvideDailyTodoDaoFactory;
import com.dopashift.data.di.DataModule_ProvideDatabaseFactory;
import com.dopashift.data.di.DataModule_ProvideEfficiencyScoreDaoFactory;
import com.dopashift.data.di.DataModule_ProvideGoalChecklistDaoFactory;
import com.dopashift.data.di.DataModule_ProvideGoalDaoFactory;
import com.dopashift.data.di.DataModule_ProvideHabitTrackDaoFactory;
import com.dopashift.data.di.DataModule_ProvideInterceptionRuleDaoFactory;
import com.dopashift.data.di.DataModule_ProvideReminderDaoFactory;
import com.dopashift.data.di.DataModule_ProvideTelemetryDaoFactory;
import com.dopashift.data.di.NetworkModule_ProvideAuthApiProviderFactory;
import com.dopashift.data.di.NetworkModule_ProvideAuthDataStoreFactory;
import com.dopashift.data.di.NetworkModule_ProvideAuthOkHttpClientFactory;
import com.dopashift.data.di.NetworkModule_ProvideAuthRetrofitFactory;
import com.dopashift.data.di.NetworkModule_ProvideDopaShiftApiFactory;
import com.dopashift.data.di.NetworkModule_ProvideMoshiFactory;
import com.dopashift.data.di.NetworkModule_ProvideOkHttpClientFactory;
import com.dopashift.data.di.NetworkModule_ProvideRetrofitFactory;
import com.dopashift.data.di.NetworkModule_ProvideTokenManagerFactory;
import com.dopashift.data.local.DopaShiftDatabase;
import com.dopashift.data.local.dao.ChangeLogDao;
import com.dopashift.data.local.dao.DailyTodoDao;
import com.dopashift.data.local.dao.EfficiencyScoreDao;
import com.dopashift.data.local.dao.GoalChecklistDao;
import com.dopashift.data.local.dao.GoalDao;
import com.dopashift.data.local.dao.HabitTrackDao;
import com.dopashift.data.local.dao.InterceptionRuleDao;
import com.dopashift.data.local.dao.ReminderDao;
import com.dopashift.data.local.dao.TelemetryDao;
import com.dopashift.data.remote.AuthApiProvider;
import com.dopashift.data.remote.AuthInterceptor;
import com.dopashift.data.remote.DopaShiftApi;
import com.dopashift.data.remote.LlmProviderServiceImpl;
import com.dopashift.data.remote.TokenManager;
import com.dopashift.data.repository.ChangeLogRepositoryImpl;
import com.dopashift.data.repository.DailyTodoRepositoryImpl;
import com.dopashift.data.repository.EfficiencyScoreRepositoryImpl;
import com.dopashift.data.repository.GoalChecklistItemRepositoryImpl;
import com.dopashift.data.repository.GoalRepositoryImpl;
import com.dopashift.data.repository.HabitTrackRepositoryImpl;
import com.dopashift.data.repository.LlmConfigRepositoryImpl;
import com.dopashift.data.repository.LocalQuietHoursProvider;
import com.dopashift.data.repository.LocalTelemetryRepository;
import com.dopashift.data.repository.ReminderRepositoryImpl;
import com.dopashift.data.repository.UserPreferencesRepositoryImpl;
import com.dopashift.interception.AllowanceTracker;
import com.dopashift.interception.DeviceCapabilityChecker;
import com.dopashift.interception.InterceptionPermissionHelper;
import com.dopashift.interception.InterceptionService;
import com.dopashift.interception.InterceptionService_MembersInjector;
import com.dopashift.interception.PermissionRevocationDetector;
import com.dopashift.interception.overlay.OverlayService;
import com.dopashift.interception.overlay.OverlayService_MembersInjector;
import com.dopashift.interception.overlay.OverlayViewModel;
import com.dopashift.interception.overlay.OverlayViewModel_HiltModules;
import com.dopashift.interception.overlay.OverlayViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.dopashift.interception.overlay.OverlayViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.dopashift.interception.reminder.InterceptionTodoReminderNotifier;
import com.dopashift.interception.reminder.di.ReminderModule_Companion_ProvideClockFactory;
import com.dopashift.ui.analytics.AnalyticsViewModel;
import com.dopashift.ui.analytics.AnalyticsViewModel_HiltModules;
import com.dopashift.ui.analytics.AnalyticsViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.dopashift.ui.analytics.AnalyticsViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.dopashift.ui.dashboard.DashboardViewModel;
import com.dopashift.ui.dashboard.DashboardViewModel_HiltModules;
import com.dopashift.ui.dashboard.DashboardViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.dopashift.ui.dashboard.DashboardViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.dopashift.ui.goals.GoalsViewModel;
import com.dopashift.ui.goals.GoalsViewModel_HiltModules;
import com.dopashift.ui.goals.GoalsViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.dopashift.ui.goals.GoalsViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.dopashift.ui.habits.HabitRoadmapViewModel;
import com.dopashift.ui.habits.HabitRoadmapViewModel_HiltModules;
import com.dopashift.ui.habits.HabitRoadmapViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.dopashift.ui.habits.HabitRoadmapViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.dopashift.ui.navigation.DopaShiftAppViewModel;
import com.dopashift.ui.navigation.DopaShiftAppViewModel_HiltModules;
import com.dopashift.ui.navigation.DopaShiftAppViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.dopashift.ui.navigation.DopaShiftAppViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.dopashift.ui.onboarding.OnboardingViewModel;
import com.dopashift.ui.onboarding.OnboardingViewModel_HiltModules;
import com.dopashift.ui.onboarding.OnboardingViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.dopashift.ui.onboarding.OnboardingViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.dopashift.ui.onboarding.PermissionSetupViewModel;
import com.dopashift.ui.onboarding.PermissionSetupViewModel_HiltModules;
import com.dopashift.ui.onboarding.PermissionSetupViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.dopashift.ui.onboarding.PermissionSetupViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.dopashift.ui.reminders.RemindersViewModel;
import com.dopashift.ui.reminders.RemindersViewModel_HiltModules;
import com.dopashift.ui.reminders.RemindersViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.dopashift.ui.reminders.RemindersViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.dopashift.ui.settings.SettingsViewModel;
import com.dopashift.ui.settings.SettingsViewModel_HiltModules;
import com.dopashift.ui.settings.SettingsViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.dopashift.ui.settings.SettingsViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.dopashift.ui.tasks.DailyTasksViewModel;
import com.dopashift.ui.tasks.DailyTasksViewModel_HiltModules;
import com.dopashift.ui.tasks.DailyTasksViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.dopashift.ui.tasks.DailyTasksViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.squareup.moshi.Moshi;
import dagger.hilt.android.ActivityRetainedLifecycle;
import dagger.hilt.android.ViewModelLifecycle;
import dagger.hilt.android.internal.builders.ActivityComponentBuilder;
import dagger.hilt.android.internal.builders.ActivityRetainedComponentBuilder;
import dagger.hilt.android.internal.builders.FragmentComponentBuilder;
import dagger.hilt.android.internal.builders.ServiceComponentBuilder;
import dagger.hilt.android.internal.builders.ViewComponentBuilder;
import dagger.hilt.android.internal.builders.ViewModelComponentBuilder;
import dagger.hilt.android.internal.builders.ViewWithFragmentComponentBuilder;
import dagger.hilt.android.internal.lifecycle.DefaultViewModelFactories;
import dagger.hilt.android.internal.lifecycle.DefaultViewModelFactories_InternalFactoryFactory_Factory;
import dagger.hilt.android.internal.managers.ActivityRetainedComponentManager_LifecycleModule_ProvideActivityRetainedLifecycleFactory;
import dagger.hilt.android.internal.managers.SavedStateHandleHolder;
import dagger.hilt.android.internal.modules.ApplicationContextModule;
import dagger.hilt.android.internal.modules.ApplicationContextModule_ProvideContextFactory;
import dagger.internal.DaggerGenerated;
import dagger.internal.DoubleCheck;
import dagger.internal.LazyClassKeyMap;
import dagger.internal.MapBuilder;
import dagger.internal.Preconditions;
import dagger.internal.Provider;
import java.time.Clock;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;

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
public final class DaggerDopaShiftApplication_HiltComponents_SingletonC {
  private DaggerDopaShiftApplication_HiltComponents_SingletonC() {
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private ApplicationContextModule applicationContextModule;

    private Builder() {
    }

    public Builder applicationContextModule(ApplicationContextModule applicationContextModule) {
      this.applicationContextModule = Preconditions.checkNotNull(applicationContextModule);
      return this;
    }

    public DopaShiftApplication_HiltComponents.SingletonC build() {
      Preconditions.checkBuilderRequirement(applicationContextModule, ApplicationContextModule.class);
      return new SingletonCImpl(applicationContextModule);
    }
  }

  private static final class ActivityRetainedCBuilder implements DopaShiftApplication_HiltComponents.ActivityRetainedC.Builder {
    private final SingletonCImpl singletonCImpl;

    private SavedStateHandleHolder savedStateHandleHolder;

    private ActivityRetainedCBuilder(SingletonCImpl singletonCImpl) {
      this.singletonCImpl = singletonCImpl;
    }

    @Override
    public ActivityRetainedCBuilder savedStateHandleHolder(
        SavedStateHandleHolder savedStateHandleHolder) {
      this.savedStateHandleHolder = Preconditions.checkNotNull(savedStateHandleHolder);
      return this;
    }

    @Override
    public DopaShiftApplication_HiltComponents.ActivityRetainedC build() {
      Preconditions.checkBuilderRequirement(savedStateHandleHolder, SavedStateHandleHolder.class);
      return new ActivityRetainedCImpl(singletonCImpl, savedStateHandleHolder);
    }
  }

  private static final class ActivityCBuilder implements DopaShiftApplication_HiltComponents.ActivityC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private Activity activity;

    private ActivityCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
    }

    @Override
    public ActivityCBuilder activity(Activity activity) {
      this.activity = Preconditions.checkNotNull(activity);
      return this;
    }

    @Override
    public DopaShiftApplication_HiltComponents.ActivityC build() {
      Preconditions.checkBuilderRequirement(activity, Activity.class);
      return new ActivityCImpl(singletonCImpl, activityRetainedCImpl, activity);
    }
  }

  private static final class FragmentCBuilder implements DopaShiftApplication_HiltComponents.FragmentC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private Fragment fragment;

    private FragmentCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
    }

    @Override
    public FragmentCBuilder fragment(Fragment fragment) {
      this.fragment = Preconditions.checkNotNull(fragment);
      return this;
    }

    @Override
    public DopaShiftApplication_HiltComponents.FragmentC build() {
      Preconditions.checkBuilderRequirement(fragment, Fragment.class);
      return new FragmentCImpl(singletonCImpl, activityRetainedCImpl, activityCImpl, fragment);
    }
  }

  private static final class ViewWithFragmentCBuilder implements DopaShiftApplication_HiltComponents.ViewWithFragmentC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final FragmentCImpl fragmentCImpl;

    private View view;

    private ViewWithFragmentCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl,
        FragmentCImpl fragmentCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
      this.fragmentCImpl = fragmentCImpl;
    }

    @Override
    public ViewWithFragmentCBuilder view(View view) {
      this.view = Preconditions.checkNotNull(view);
      return this;
    }

    @Override
    public DopaShiftApplication_HiltComponents.ViewWithFragmentC build() {
      Preconditions.checkBuilderRequirement(view, View.class);
      return new ViewWithFragmentCImpl(singletonCImpl, activityRetainedCImpl, activityCImpl, fragmentCImpl, view);
    }
  }

  private static final class ViewCBuilder implements DopaShiftApplication_HiltComponents.ViewC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private View view;

    private ViewCBuilder(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        ActivityCImpl activityCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
    }

    @Override
    public ViewCBuilder view(View view) {
      this.view = Preconditions.checkNotNull(view);
      return this;
    }

    @Override
    public DopaShiftApplication_HiltComponents.ViewC build() {
      Preconditions.checkBuilderRequirement(view, View.class);
      return new ViewCImpl(singletonCImpl, activityRetainedCImpl, activityCImpl, view);
    }
  }

  private static final class ViewModelCBuilder implements DopaShiftApplication_HiltComponents.ViewModelC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private SavedStateHandle savedStateHandle;

    private ViewModelLifecycle viewModelLifecycle;

    private ViewModelCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
    }

    @Override
    public ViewModelCBuilder savedStateHandle(SavedStateHandle handle) {
      this.savedStateHandle = Preconditions.checkNotNull(handle);
      return this;
    }

    @Override
    public ViewModelCBuilder viewModelLifecycle(ViewModelLifecycle viewModelLifecycle) {
      this.viewModelLifecycle = Preconditions.checkNotNull(viewModelLifecycle);
      return this;
    }

    @Override
    public DopaShiftApplication_HiltComponents.ViewModelC build() {
      Preconditions.checkBuilderRequirement(savedStateHandle, SavedStateHandle.class);
      Preconditions.checkBuilderRequirement(viewModelLifecycle, ViewModelLifecycle.class);
      return new ViewModelCImpl(singletonCImpl, activityRetainedCImpl, savedStateHandle, viewModelLifecycle);
    }
  }

  private static final class ServiceCBuilder implements DopaShiftApplication_HiltComponents.ServiceC.Builder {
    private final SingletonCImpl singletonCImpl;

    private Service service;

    private ServiceCBuilder(SingletonCImpl singletonCImpl) {
      this.singletonCImpl = singletonCImpl;
    }

    @Override
    public ServiceCBuilder service(Service service) {
      this.service = Preconditions.checkNotNull(service);
      return this;
    }

    @Override
    public DopaShiftApplication_HiltComponents.ServiceC build() {
      Preconditions.checkBuilderRequirement(service, Service.class);
      return new ServiceCImpl(singletonCImpl, service);
    }
  }

  private static final class ViewWithFragmentCImpl extends DopaShiftApplication_HiltComponents.ViewWithFragmentC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final FragmentCImpl fragmentCImpl;

    private final ViewWithFragmentCImpl viewWithFragmentCImpl = this;

    ViewWithFragmentCImpl(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl,
        FragmentCImpl fragmentCImpl, View viewParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
      this.fragmentCImpl = fragmentCImpl;


    }
  }

  private static final class FragmentCImpl extends DopaShiftApplication_HiltComponents.FragmentC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final FragmentCImpl fragmentCImpl = this;

    FragmentCImpl(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        ActivityCImpl activityCImpl, Fragment fragmentParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;


    }

    @Override
    public DefaultViewModelFactories.InternalFactoryFactory getHiltInternalFactoryFactory() {
      return activityCImpl.getHiltInternalFactoryFactory();
    }

    @Override
    public ViewWithFragmentComponentBuilder viewWithFragmentComponentBuilder() {
      return new ViewWithFragmentCBuilder(singletonCImpl, activityRetainedCImpl, activityCImpl, fragmentCImpl);
    }
  }

  private static final class ViewCImpl extends DopaShiftApplication_HiltComponents.ViewC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final ViewCImpl viewCImpl = this;

    ViewCImpl(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        ActivityCImpl activityCImpl, View viewParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;


    }
  }

  private static final class ActivityCImpl extends DopaShiftApplication_HiltComponents.ActivityC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl = this;

    ActivityCImpl(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        Activity activityParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;


    }

    @Override
    public void injectMainActivity(MainActivity arg0) {
      injectMainActivity2(arg0);
    }

    @Override
    public DefaultViewModelFactories.InternalFactoryFactory getHiltInternalFactoryFactory() {
      return DefaultViewModelFactories_InternalFactoryFactory_Factory.newInstance(getViewModelKeys(), new ViewModelCBuilder(singletonCImpl, activityRetainedCImpl));
    }

    @Override
    public Map<Class<?>, Boolean> getViewModelKeys() {
      return LazyClassKeyMap.<Boolean>of(MapBuilder.<String, Boolean>newMapBuilder(11).put(AnalyticsViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, AnalyticsViewModel_HiltModules.KeyModule.provide()).put(DailyTasksViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, DailyTasksViewModel_HiltModules.KeyModule.provide()).put(DashboardViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, DashboardViewModel_HiltModules.KeyModule.provide()).put(DopaShiftAppViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, DopaShiftAppViewModel_HiltModules.KeyModule.provide()).put(GoalsViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, GoalsViewModel_HiltModules.KeyModule.provide()).put(HabitRoadmapViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, HabitRoadmapViewModel_HiltModules.KeyModule.provide()).put(OnboardingViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, OnboardingViewModel_HiltModules.KeyModule.provide()).put(OverlayViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, OverlayViewModel_HiltModules.KeyModule.provide()).put(PermissionSetupViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, PermissionSetupViewModel_HiltModules.KeyModule.provide()).put(RemindersViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, RemindersViewModel_HiltModules.KeyModule.provide()).put(SettingsViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, SettingsViewModel_HiltModules.KeyModule.provide()).build());
    }

    @Override
    public ViewModelComponentBuilder getViewModelComponentBuilder() {
      return new ViewModelCBuilder(singletonCImpl, activityRetainedCImpl);
    }

    @Override
    public FragmentComponentBuilder fragmentComponentBuilder() {
      return new FragmentCBuilder(singletonCImpl, activityRetainedCImpl, activityCImpl);
    }

    @Override
    public ViewComponentBuilder viewComponentBuilder() {
      return new ViewCBuilder(singletonCImpl, activityRetainedCImpl, activityCImpl);
    }

    private MainActivity injectMainActivity2(MainActivity instance) {
      MainActivity_MembersInjector.injectPermissionRevocationDetector(instance, singletonCImpl.permissionRevocationDetectorProvider.get());
      MainActivity_MembersInjector.injectPermissionHelper(instance, singletonCImpl.interceptionPermissionHelperProvider.get());
      return instance;
    }
  }

  private static final class ViewModelCImpl extends DopaShiftApplication_HiltComponents.ViewModelC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ViewModelCImpl viewModelCImpl = this;

    Provider<AnalyticsViewModel> analyticsViewModelProvider;

    Provider<DailyTasksViewModel> dailyTasksViewModelProvider;

    Provider<DashboardViewModel> dashboardViewModelProvider;

    Provider<DopaShiftAppViewModel> dopaShiftAppViewModelProvider;

    Provider<GoalsViewModel> goalsViewModelProvider;

    Provider<HabitRoadmapViewModel> habitRoadmapViewModelProvider;

    Provider<OnboardingViewModel> onboardingViewModelProvider;

    Provider<OverlayViewModel> overlayViewModelProvider;

    Provider<PermissionSetupViewModel> permissionSetupViewModelProvider;

    Provider<RemindersViewModel> remindersViewModelProvider;

    Provider<SettingsViewModel> settingsViewModelProvider;

    ViewModelCImpl(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        SavedStateHandle savedStateHandleParam, ViewModelLifecycle viewModelLifecycleParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;

      initialize(savedStateHandleParam, viewModelLifecycleParam);

    }

    @SuppressWarnings("unchecked")
    private void initialize(final SavedStateHandle savedStateHandleParam,
        final ViewModelLifecycle viewModelLifecycleParam) {
      this.analyticsViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 0);
      this.dailyTasksViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 1);
      this.dashboardViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 2);
      this.dopaShiftAppViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 3);
      this.goalsViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 4);
      this.habitRoadmapViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 5);
      this.onboardingViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 6);
      this.overlayViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 7);
      this.permissionSetupViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 8);
      this.remindersViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 9);
      this.settingsViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 10);
    }

    @Override
    public Map<Class<?>, javax.inject.Provider<ViewModel>> getHiltViewModelMap() {
      return LazyClassKeyMap.<javax.inject.Provider<ViewModel>>of(MapBuilder.<String, javax.inject.Provider<ViewModel>>newMapBuilder(11).put(AnalyticsViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (analyticsViewModelProvider))).put(DailyTasksViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (dailyTasksViewModelProvider))).put(DashboardViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (dashboardViewModelProvider))).put(DopaShiftAppViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (dopaShiftAppViewModelProvider))).put(GoalsViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (goalsViewModelProvider))).put(HabitRoadmapViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (habitRoadmapViewModelProvider))).put(OnboardingViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (onboardingViewModelProvider))).put(OverlayViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (overlayViewModelProvider))).put(PermissionSetupViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (permissionSetupViewModelProvider))).put(RemindersViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (remindersViewModelProvider))).put(SettingsViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (settingsViewModelProvider))).build());
    }

    @Override
    public Map<Class<?>, Object> getHiltViewModelAssistedMap() {
      return Collections.<Class<?>, Object>emptyMap();
    }

    private static final class SwitchingProvider<T> implements Provider<T> {
      private final SingletonCImpl singletonCImpl;

      private final ActivityRetainedCImpl activityRetainedCImpl;

      private final ViewModelCImpl viewModelCImpl;

      private final int id;

      SwitchingProvider(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
          ViewModelCImpl viewModelCImpl, int id) {
        this.singletonCImpl = singletonCImpl;
        this.activityRetainedCImpl = activityRetainedCImpl;
        this.viewModelCImpl = viewModelCImpl;
        this.id = id;
      }

      @Override
      @SuppressWarnings("unchecked")
      public T get() {
        switch (id) {
          case 0: // com.dopashift.ui.analytics.AnalyticsViewModel
          return (T) new AnalyticsViewModel(singletonCImpl.efficiencyScoreRepositoryImplProvider.get());

          case 1: // com.dopashift.ui.tasks.DailyTasksViewModel
          return (T) new DailyTasksViewModel(singletonCImpl.dailyTodoRepositoryImplProvider.get());

          case 2: // com.dopashift.ui.dashboard.DashboardViewModel
          return (T) new DashboardViewModel(singletonCImpl.goalRepositoryImplProvider.get(), singletonCImpl.dailyTodoRepositoryImplProvider.get(), singletonCImpl.habitTrackRepositoryImplProvider.get(), singletonCImpl.efficiencyScoreRepositoryImplProvider.get(), singletonCImpl.changeLogRepositoryImplProvider.get(), singletonCImpl.goalChecklistItemRepositoryImplProvider.get());

          case 3: // com.dopashift.ui.navigation.DopaShiftAppViewModel
          return (T) new DopaShiftAppViewModel(singletonCImpl.userPreferencesRepositoryImplProvider.get());

          case 4: // com.dopashift.ui.goals.GoalsViewModel
          return (T) new GoalsViewModel(singletonCImpl.goalRepositoryImplProvider.get(), singletonCImpl.goalChecklistItemRepositoryImplProvider.get(), singletonCImpl.habitTrackRepositoryImplProvider.get());

          case 5: // com.dopashift.ui.habits.HabitRoadmapViewModel
          return (T) new HabitRoadmapViewModel(singletonCImpl.habitTrackRepositoryImplProvider.get(), singletonCImpl.goalRepositoryImplProvider.get());

          case 6: // com.dopashift.ui.onboarding.OnboardingViewModel
          return (T) new OnboardingViewModel(singletonCImpl.goalRepositoryImplProvider.get(), singletonCImpl.habitTrackRepositoryImplProvider.get(), singletonCImpl.userPreferencesRepositoryImplProvider.get());

          case 7: // com.dopashift.interception.overlay.OverlayViewModel
          return (T) new OverlayViewModel(singletonCImpl.dailyTodoRepositoryImplProvider.get(), singletonCImpl.habitTrackRepositoryImplProvider.get());

          case 8: // com.dopashift.ui.onboarding.PermissionSetupViewModel
          return (T) new PermissionSetupViewModel(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 9: // com.dopashift.ui.reminders.RemindersViewModel
          return (T) new RemindersViewModel(singletonCImpl.reminderRepositoryImplProvider.get());

          case 10: // com.dopashift.ui.settings.SettingsViewModel
          return (T) new SettingsViewModel(singletonCImpl.userPreferencesRepositoryImplProvider.get(), singletonCImpl.llmConfigRepositoryImplProvider.get(), singletonCImpl.localQuietHoursProvider.get(), singletonCImpl.llmProviderServiceImplProvider.get());

          default: throw new AssertionError(id);
        }
      }
    }
  }

  private static final class ActivityRetainedCImpl extends DopaShiftApplication_HiltComponents.ActivityRetainedC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl = this;

    Provider<ActivityRetainedLifecycle> provideActivityRetainedLifecycleProvider;

    ActivityRetainedCImpl(SingletonCImpl singletonCImpl,
        SavedStateHandleHolder savedStateHandleHolderParam) {
      this.singletonCImpl = singletonCImpl;

      initialize(savedStateHandleHolderParam);

    }

    @SuppressWarnings("unchecked")
    private void initialize(final SavedStateHandleHolder savedStateHandleHolderParam) {
      this.provideActivityRetainedLifecycleProvider = DoubleCheck.provider(new SwitchingProvider<ActivityRetainedLifecycle>(singletonCImpl, activityRetainedCImpl, 0));
    }

    @Override
    public ActivityComponentBuilder activityComponentBuilder() {
      return new ActivityCBuilder(singletonCImpl, activityRetainedCImpl);
    }

    @Override
    public ActivityRetainedLifecycle getActivityRetainedLifecycle() {
      return provideActivityRetainedLifecycleProvider.get();
    }

    private static final class SwitchingProvider<T> implements Provider<T> {
      private final SingletonCImpl singletonCImpl;

      private final ActivityRetainedCImpl activityRetainedCImpl;

      private final int id;

      SwitchingProvider(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
          int id) {
        this.singletonCImpl = singletonCImpl;
        this.activityRetainedCImpl = activityRetainedCImpl;
        this.id = id;
      }

      @Override
      @SuppressWarnings("unchecked")
      public T get() {
        switch (id) {
          case 0: // dagger.hilt.android.ActivityRetainedLifecycle
          return (T) ActivityRetainedComponentManager_LifecycleModule_ProvideActivityRetainedLifecycleFactory.provideActivityRetainedLifecycle();

          default: throw new AssertionError(id);
        }
      }
    }
  }

  private static final class ServiceCImpl extends DopaShiftApplication_HiltComponents.ServiceC {
    private final SingletonCImpl singletonCImpl;

    private final ServiceCImpl serviceCImpl = this;

    ServiceCImpl(SingletonCImpl singletonCImpl, Service serviceParam) {
      this.singletonCImpl = singletonCImpl;


    }

    @Override
    public void injectInterceptionService(InterceptionService arg0) {
      injectInterceptionService2(arg0);
    }

    @Override
    public void injectOverlayService(OverlayService arg0) {
      injectOverlayService2(arg0);
    }

    private InterceptionService injectInterceptionService2(InterceptionService instance) {
      InterceptionService_MembersInjector.injectPermissionHelper(instance, singletonCImpl.interceptionPermissionHelperProvider.get());
      InterceptionService_MembersInjector.injectDeviceCapabilityChecker(instance, singletonCImpl.deviceCapabilityCheckerProvider.get());
      InterceptionService_MembersInjector.injectAllowanceTracker(instance, singletonCImpl.allowanceTrackerProvider.get());
      InterceptionService_MembersInjector.injectTodoReminderNotifier(instance, singletonCImpl.interceptionTodoReminderNotifierProvider.get());
      return instance;
    }

    private OverlayService injectOverlayService2(OverlayService instance2) {
      OverlayService_MembersInjector.injectDailyTodoRepository(instance2, singletonCImpl.dailyTodoRepositoryImplProvider.get());
      OverlayService_MembersInjector.injectHabitTrackRepository(instance2, singletonCImpl.habitTrackRepositoryImplProvider.get());
      return instance2;
    }
  }

  private static final class SingletonCImpl extends DopaShiftApplication_HiltComponents.SingletonC {
    private final ApplicationContextModule applicationContextModule;

    private final SingletonCImpl singletonCImpl = this;

    Provider<InterceptionPermissionHelper> interceptionPermissionHelperProvider;

    Provider<PermissionRevocationDetector> permissionRevocationDetectorProvider;

    Provider<DopaShiftDatabase> provideDatabaseProvider;

    Provider<EfficiencyScoreRepositoryImpl> efficiencyScoreRepositoryImplProvider;

    Provider<DailyTodoRepositoryImpl> dailyTodoRepositoryImplProvider;

    Provider<GoalRepositoryImpl> goalRepositoryImplProvider;

    Provider<HabitTrackRepositoryImpl> habitTrackRepositoryImplProvider;

    Provider<ChangeLogRepositoryImpl> changeLogRepositoryImplProvider;

    Provider<GoalChecklistItemRepositoryImpl> goalChecklistItemRepositoryImplProvider;

    Provider<UserPreferencesRepositoryImpl> userPreferencesRepositoryImplProvider;

    Provider<ReminderRepositoryImpl> reminderRepositoryImplProvider;

    Provider<LlmConfigRepositoryImpl> llmConfigRepositoryImplProvider;

    Provider<LocalQuietHoursProvider> localQuietHoursProvider;

    Provider<DataStore<Preferences>> provideAuthDataStoreProvider;

    Provider<OkHttpClient> provideAuthOkHttpClientProvider;

    Provider<Moshi> provideMoshiProvider;

    Provider<Retrofit> provideAuthRetrofitProvider;

    Provider<AuthApiProvider> provideAuthApiProvider;

    Provider<TokenManager> provideTokenManagerProvider;

    Provider<AuthInterceptor> authInterceptorProvider;

    Provider<OkHttpClient> provideOkHttpClientProvider;

    Provider<Retrofit> provideRetrofitProvider;

    Provider<DopaShiftApi> provideDopaShiftApiProvider;

    Provider<LlmProviderServiceImpl> llmProviderServiceImplProvider;

    Provider<DeviceCapabilityChecker> deviceCapabilityCheckerProvider;

    Provider<LocalTelemetryRepository> localTelemetryRepositoryProvider;

    Provider<Clock> provideClockProvider;

    Provider<AllowanceTracker> allowanceTrackerProvider;

    Provider<InterceptionTodoReminderNotifier> interceptionTodoReminderNotifierProvider;

    SingletonCImpl(ApplicationContextModule applicationContextModuleParam) {
      this.applicationContextModule = applicationContextModuleParam;
      initialize(applicationContextModuleParam);
      initialize2(applicationContextModuleParam);

    }

    EfficiencyScoreDao efficiencyScoreDao() {
      return DataModule_ProvideEfficiencyScoreDaoFactory.provideEfficiencyScoreDao(provideDatabaseProvider.get());
    }

    DailyTodoDao dailyTodoDao() {
      return DataModule_ProvideDailyTodoDaoFactory.provideDailyTodoDao(provideDatabaseProvider.get());
    }

    GoalDao goalDao() {
      return DataModule_ProvideGoalDaoFactory.provideGoalDao(provideDatabaseProvider.get());
    }

    HabitTrackDao habitTrackDao() {
      return DataModule_ProvideHabitTrackDaoFactory.provideHabitTrackDao(provideDatabaseProvider.get());
    }

    ChangeLogDao changeLogDao() {
      return DataModule_ProvideChangeLogDaoFactory.provideChangeLogDao(provideDatabaseProvider.get());
    }

    GoalChecklistDao goalChecklistDao() {
      return DataModule_ProvideGoalChecklistDaoFactory.provideGoalChecklistDao(provideDatabaseProvider.get());
    }

    ReminderDao reminderDao() {
      return DataModule_ProvideReminderDaoFactory.provideReminderDao(provideDatabaseProvider.get());
    }

    InterceptionRuleDao interceptionRuleDao() {
      return DataModule_ProvideInterceptionRuleDaoFactory.provideInterceptionRuleDao(provideDatabaseProvider.get());
    }

    TelemetryDao telemetryDao() {
      return DataModule_ProvideTelemetryDaoFactory.provideTelemetryDao(provideDatabaseProvider.get());
    }

    @SuppressWarnings("unchecked")
    private void initialize(final ApplicationContextModule applicationContextModuleParam) {
      this.interceptionPermissionHelperProvider = DoubleCheck.provider(new SwitchingProvider<InterceptionPermissionHelper>(singletonCImpl, 1));
      this.permissionRevocationDetectorProvider = DoubleCheck.provider(new SwitchingProvider<PermissionRevocationDetector>(singletonCImpl, 0));
      this.provideDatabaseProvider = DoubleCheck.provider(new SwitchingProvider<DopaShiftDatabase>(singletonCImpl, 3));
      this.efficiencyScoreRepositoryImplProvider = DoubleCheck.provider(new SwitchingProvider<EfficiencyScoreRepositoryImpl>(singletonCImpl, 2));
      this.dailyTodoRepositoryImplProvider = DoubleCheck.provider(new SwitchingProvider<DailyTodoRepositoryImpl>(singletonCImpl, 4));
      this.goalRepositoryImplProvider = DoubleCheck.provider(new SwitchingProvider<GoalRepositoryImpl>(singletonCImpl, 5));
      this.habitTrackRepositoryImplProvider = DoubleCheck.provider(new SwitchingProvider<HabitTrackRepositoryImpl>(singletonCImpl, 6));
      this.changeLogRepositoryImplProvider = DoubleCheck.provider(new SwitchingProvider<ChangeLogRepositoryImpl>(singletonCImpl, 7));
      this.goalChecklistItemRepositoryImplProvider = DoubleCheck.provider(new SwitchingProvider<GoalChecklistItemRepositoryImpl>(singletonCImpl, 8));
      this.userPreferencesRepositoryImplProvider = DoubleCheck.provider(new SwitchingProvider<UserPreferencesRepositoryImpl>(singletonCImpl, 9));
      this.reminderRepositoryImplProvider = DoubleCheck.provider(new SwitchingProvider<ReminderRepositoryImpl>(singletonCImpl, 10));
      this.llmConfigRepositoryImplProvider = DoubleCheck.provider(new SwitchingProvider<LlmConfigRepositoryImpl>(singletonCImpl, 11));
      this.localQuietHoursProvider = DoubleCheck.provider(new SwitchingProvider<LocalQuietHoursProvider>(singletonCImpl, 12));
      this.provideAuthDataStoreProvider = DoubleCheck.provider(new SwitchingProvider<DataStore<Preferences>>(singletonCImpl, 19));
      this.provideAuthOkHttpClientProvider = DoubleCheck.provider(new SwitchingProvider<OkHttpClient>(singletonCImpl, 22));
      this.provideMoshiProvider = DoubleCheck.provider(new SwitchingProvider<Moshi>(singletonCImpl, 23));
      this.provideAuthRetrofitProvider = DoubleCheck.provider(new SwitchingProvider<Retrofit>(singletonCImpl, 21));
      this.provideAuthApiProvider = DoubleCheck.provider(new SwitchingProvider<AuthApiProvider>(singletonCImpl, 20));
      this.provideTokenManagerProvider = DoubleCheck.provider(new SwitchingProvider<TokenManager>(singletonCImpl, 18));
      this.authInterceptorProvider = DoubleCheck.provider(new SwitchingProvider<AuthInterceptor>(singletonCImpl, 17));
      this.provideOkHttpClientProvider = DoubleCheck.provider(new SwitchingProvider<OkHttpClient>(singletonCImpl, 16));
      this.provideRetrofitProvider = DoubleCheck.provider(new SwitchingProvider<Retrofit>(singletonCImpl, 15));
      this.provideDopaShiftApiProvider = DoubleCheck.provider(new SwitchingProvider<DopaShiftApi>(singletonCImpl, 14));
      this.llmProviderServiceImplProvider = DoubleCheck.provider(new SwitchingProvider<LlmProviderServiceImpl>(singletonCImpl, 13));
      this.deviceCapabilityCheckerProvider = DoubleCheck.provider(new SwitchingProvider<DeviceCapabilityChecker>(singletonCImpl, 24));
    }

    @SuppressWarnings("unchecked")
    private void initialize2(final ApplicationContextModule applicationContextModuleParam) {
      this.localTelemetryRepositoryProvider = DoubleCheck.provider(new SwitchingProvider<LocalTelemetryRepository>(singletonCImpl, 26));
      this.provideClockProvider = DoubleCheck.provider(new SwitchingProvider<Clock>(singletonCImpl, 27));
      this.allowanceTrackerProvider = DoubleCheck.provider(new SwitchingProvider<AllowanceTracker>(singletonCImpl, 25));
      this.interceptionTodoReminderNotifierProvider = DoubleCheck.provider(new SwitchingProvider<InterceptionTodoReminderNotifier>(singletonCImpl, 28));
    }

    @Override
    public void injectDopaShiftApplication(DopaShiftApplication arg0) {
    }

    @Override
    public Set<Boolean> getDisableFragmentGetContextFix() {
      return Collections.<Boolean>emptySet();
    }

    @Override
    public ActivityRetainedComponentBuilder retainedComponentBuilder() {
      return new ActivityRetainedCBuilder(singletonCImpl);
    }

    @Override
    public ServiceComponentBuilder serviceComponentBuilder() {
      return new ServiceCBuilder(singletonCImpl);
    }

    private static final class SwitchingProvider<T> implements Provider<T> {
      private final SingletonCImpl singletonCImpl;

      private final int id;

      SwitchingProvider(SingletonCImpl singletonCImpl, int id) {
        this.singletonCImpl = singletonCImpl;
        this.id = id;
      }

      @Override
      @SuppressWarnings("unchecked")
      public T get() {
        switch (id) {
          case 0: // com.dopashift.interception.PermissionRevocationDetector
          return (T) new PermissionRevocationDetector(singletonCImpl.interceptionPermissionHelperProvider.get());

          case 1: // com.dopashift.interception.InterceptionPermissionHelper
          return (T) new InterceptionPermissionHelper();

          case 2: // com.dopashift.data.repository.EfficiencyScoreRepositoryImpl
          return (T) new EfficiencyScoreRepositoryImpl(singletonCImpl.efficiencyScoreDao());

          case 3: // com.dopashift.data.local.DopaShiftDatabase
          return (T) DataModule_ProvideDatabaseFactory.provideDatabase(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 4: // com.dopashift.data.repository.DailyTodoRepositoryImpl
          return (T) new DailyTodoRepositoryImpl(singletonCImpl.dailyTodoDao());

          case 5: // com.dopashift.data.repository.GoalRepositoryImpl
          return (T) new GoalRepositoryImpl(singletonCImpl.goalDao());

          case 6: // com.dopashift.data.repository.HabitTrackRepositoryImpl
          return (T) new HabitTrackRepositoryImpl(singletonCImpl.habitTrackDao());

          case 7: // com.dopashift.data.repository.ChangeLogRepositoryImpl
          return (T) new ChangeLogRepositoryImpl(singletonCImpl.changeLogDao());

          case 8: // com.dopashift.data.repository.GoalChecklistItemRepositoryImpl
          return (T) new GoalChecklistItemRepositoryImpl(singletonCImpl.goalChecklistDao());

          case 9: // com.dopashift.data.repository.UserPreferencesRepositoryImpl
          return (T) new UserPreferencesRepositoryImpl(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 10: // com.dopashift.data.repository.ReminderRepositoryImpl
          return (T) new ReminderRepositoryImpl(singletonCImpl.reminderDao());

          case 11: // com.dopashift.data.repository.LlmConfigRepositoryImpl
          return (T) new LlmConfigRepositoryImpl(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 12: // com.dopashift.data.repository.LocalQuietHoursProvider
          return (T) new LocalQuietHoursProvider(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 13: // com.dopashift.data.remote.LlmProviderServiceImpl
          return (T) new LlmProviderServiceImpl(singletonCImpl.provideDopaShiftApiProvider.get());

          case 14: // com.dopashift.data.remote.DopaShiftApi
          return (T) NetworkModule_ProvideDopaShiftApiFactory.provideDopaShiftApi(singletonCImpl.provideRetrofitProvider.get());

          case 15: // retrofit2.Retrofit
          return (T) NetworkModule_ProvideRetrofitFactory.provideRetrofit(singletonCImpl.provideOkHttpClientProvider.get(), singletonCImpl.provideMoshiProvider.get());

          case 16: // okhttp3.OkHttpClient
          return (T) NetworkModule_ProvideOkHttpClientFactory.provideOkHttpClient(singletonCImpl.authInterceptorProvider.get());

          case 17: // com.dopashift.data.remote.AuthInterceptor
          return (T) new AuthInterceptor(singletonCImpl.provideTokenManagerProvider.get());

          case 18: // com.dopashift.data.remote.TokenManager
          return (T) NetworkModule_ProvideTokenManagerFactory.provideTokenManager(singletonCImpl.provideAuthDataStoreProvider.get(), singletonCImpl.provideAuthApiProvider.get());

          case 19: // androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>
          return (T) NetworkModule_ProvideAuthDataStoreFactory.provideAuthDataStore(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 20: // com.dopashift.data.remote.AuthApiProvider
          return (T) NetworkModule_ProvideAuthApiProviderFactory.provideAuthApiProvider(singletonCImpl.provideAuthRetrofitProvider.get());

          case 21: // @com.dopashift.data.di.AuthClient retrofit2.Retrofit
          return (T) NetworkModule_ProvideAuthRetrofitFactory.provideAuthRetrofit(singletonCImpl.provideAuthOkHttpClientProvider.get(), singletonCImpl.provideMoshiProvider.get());

          case 22: // @com.dopashift.data.di.AuthClient okhttp3.OkHttpClient
          return (T) NetworkModule_ProvideAuthOkHttpClientFactory.provideAuthOkHttpClient();

          case 23: // com.squareup.moshi.Moshi
          return (T) NetworkModule_ProvideMoshiFactory.provideMoshi();

          case 24: // com.dopashift.interception.DeviceCapabilityChecker
          return (T) new DeviceCapabilityChecker();

          case 25: // com.dopashift.interception.AllowanceTracker
          return (T) new AllowanceTracker(singletonCImpl.interceptionRuleDao(), singletonCImpl.localTelemetryRepositoryProvider.get(), singletonCImpl.provideClockProvider.get());

          case 26: // com.dopashift.data.repository.LocalTelemetryRepository
          return (T) new LocalTelemetryRepository(singletonCImpl.telemetryDao());

          case 27: // java.time.Clock
          return (T) ReminderModule_Companion_ProvideClockFactory.provideClock();

          case 28: // com.dopashift.interception.reminder.InterceptionTodoReminderNotifier
          return (T) new InterceptionTodoReminderNotifier(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule), singletonCImpl.dailyTodoRepositoryImplProvider.get());

          default: throw new AssertionError(id);
        }
      }
    }
  }
}
