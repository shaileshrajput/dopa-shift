package com.dopashift.app;

import android.app.Activity;
import android.app.Service;
import android.view.View;
import androidx.datastore.core.DataStore;
import androidx.datastore.preferences.core.Preferences;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import com.dopashift.app.creation.AndroidDeviceIdProvider;
import com.dopashift.app.creation.SystemDomainClock;
import com.dopashift.app.di.QuickCreateUseCaseModule_ProvideCorrelationIdFactoryFactory;
import com.dopashift.app.di.QuickCreateUseCaseModule_ProvideCreateDailyTodoUseCaseFactory;
import com.dopashift.app.di.QuickCreateUseCaseModule_ProvideCreateGoalUseCaseFactory;
import com.dopashift.app.di.QuickCreateUseCaseModule_ProvideCreateGoalWithHabitUseCaseFactory;
import com.dopashift.app.di.QuickCreateUseCaseModule_ProvideCreateHabitTrackUseCaseFactory;
import com.dopashift.app.di.QuickCreateUseCaseModule_ProvideGoalSuggestionServiceFactory;
import com.dopashift.app.di.QuickCreateUseCaseModule_ProvideHabitAuthoringResolverFactory;
import com.dopashift.app.di.QuickCreateUseCaseModule_ProvideObserveDashboardSummaryUseCaseFactory;
import com.dopashift.app.di.QuickCreateUseCaseModule_ProvideUndoCreationUseCaseFactory;
import com.dopashift.data.asset.AssetCategoryKeywordProvider;
import com.dopashift.data.asset.AssetHabitTemplateProvider;
import com.dopashift.data.di.DataModule_ProvideChangeLogDaoFactory;
import com.dopashift.data.di.DataModule_ProvideDailyTodoDaoFactory;
import com.dopashift.data.di.DataModule_ProvideDatabaseFactory;
import com.dopashift.data.di.DataModule_ProvideDiagnosticEventDaoFactory;
import com.dopashift.data.di.DataModule_ProvideEfficiencyScoreDaoFactory;
import com.dopashift.data.di.DataModule_ProvideGoalChecklistDaoFactory;
import com.dopashift.data.di.DataModule_ProvideGoalDaoFactory;
import com.dopashift.data.di.DataModule_ProvideHabitTrackDaoFactory;
import com.dopashift.data.di.DataModule_ProvideInterceptActionAuditDaoFactory;
import com.dopashift.data.di.DataModule_ProvideInterceptionRuleDaoFactory;
import com.dopashift.data.di.DataModule_ProvideReminderDaoFactory;
import com.dopashift.data.di.DataModule_ProvideSyncStateDaoFactory;
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
import com.dopashift.data.local.dao.DiagnosticEventDao;
import com.dopashift.data.local.dao.EfficiencyScoreDao;
import com.dopashift.data.local.dao.GoalChecklistDao;
import com.dopashift.data.local.dao.GoalDao;
import com.dopashift.data.local.dao.HabitTrackDao;
import com.dopashift.data.local.dao.InterceptActionAuditDao;
import com.dopashift.data.local.dao.InterceptionRuleDao;
import com.dopashift.data.local.dao.ReminderDao;
import com.dopashift.data.local.dao.SyncStateDao;
import com.dopashift.data.local.dao.TelemetryDao;
import com.dopashift.data.remote.AuthApiProvider;
import com.dopashift.data.remote.AuthInterceptor;
import com.dopashift.data.remote.DopaShiftApi;
import com.dopashift.data.remote.LlmProviderServiceImpl;
import com.dopashift.data.remote.TokenManager;
import com.dopashift.data.repository.ChangeLogRepositoryImpl;
import com.dopashift.data.repository.DailyTodoRepositoryImpl;
import com.dopashift.data.repository.DataStoreDiagnosticsSink;
import com.dopashift.data.repository.EfficiencyScoreRepositoryImpl;
import com.dopashift.data.repository.GoalChecklistItemRepositoryImpl;
import com.dopashift.data.repository.GoalRepositoryImpl;
import com.dopashift.data.repository.HabitTrackRepositoryImpl;
import com.dopashift.data.repository.InterceptActionAuditRepositoryImpl;
import com.dopashift.data.repository.InterceptionRuleRepositoryImpl;
import com.dopashift.data.repository.LlmConfigRepositoryImpl;
import com.dopashift.data.repository.LocalQuietHoursProvider;
import com.dopashift.data.repository.LocalTelemetryRepository;
import com.dopashift.data.repository.QuickCreatePreferencesStore;
import com.dopashift.data.repository.ReminderRepositoryImpl;
import com.dopashift.data.repository.RoomDashboardSummaryRepository;
import com.dopashift.data.repository.RoomTransactionRunner;
import com.dopashift.data.repository.UserPreferencesRepositoryImpl;
import com.dopashift.domain.creation.CorrelationIdFactory;
import com.dopashift.domain.creation.GoalSuggestionService;
import com.dopashift.domain.creation.HabitAuthoringResolver;
import com.dopashift.domain.creation.usecase.CreateDailyTodoUseCase;
import com.dopashift.domain.creation.usecase.CreateGoalUseCase;
import com.dopashift.domain.creation.usecase.CreateGoalWithHabitUseCase;
import com.dopashift.domain.creation.usecase.CreateHabitTrackUseCase;
import com.dopashift.domain.creation.usecase.ObserveDashboardSummaryUseCase;
import com.dopashift.domain.creation.usecase.UndoCreationUseCase;
import com.dopashift.interception.AllowanceTracker;
import com.dopashift.interception.DeferredInterceptCoordinator;
import com.dopashift.interception.DeviceCapabilityChecker;
import com.dopashift.interception.FocusExtensionManager;
import com.dopashift.interception.InterceptionPermissionHelper;
import com.dopashift.interception.InterceptionService;
import com.dopashift.interception.InterceptionService_MembersInjector;
import com.dopashift.interception.PermissionRevocationDetector;
import com.dopashift.interception.RepetitiveSessionTracker;
import com.dopashift.interception.SuppressionContextDetector;
import com.dopashift.interception.SuppressionSignalSource;
import com.dopashift.interception.di.InterceptionModule_ProvideSuppressionSignalSourceFactory;
import com.dopashift.interception.di.InterceptionModule_ProvideZoneIdFactory;
import com.dopashift.interception.overlay.OverlayInlineGoalCaptureHandler;
import com.dopashift.interception.overlay.OverlayService;
import com.dopashift.interception.overlay.OverlayService_MembersInjector;
import com.dopashift.interception.overlay.OverlayTodoCompletionHandler;
import com.dopashift.interception.overlay.OverlayViewModel;
import com.dopashift.interception.overlay.OverlayViewModel_HiltModules;
import com.dopashift.interception.overlay.OverlayViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.dopashift.interception.overlay.OverlayViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.dopashift.interception.reminder.InterceptionTodoReminderNotifier;
import com.dopashift.interception.reminder.LimitReachedNotifier;
import com.dopashift.interception.reminder.di.ReminderModule_Companion_ProvideClockFactory;
import com.dopashift.sync.SyncManager;
import com.dopashift.sync.SyncStatusProviderImpl;
import com.dopashift.sync.di.SyncModule_ProvideSyncManagerFactory;
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
import com.dopashift.ui.interception.InstalledAppsProvider;
import com.dopashift.ui.interception.RuleAuthoringViewModel;
import com.dopashift.ui.interception.RuleAuthoringViewModel_HiltModules;
import com.dopashift.ui.interception.RuleAuthoringViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.dopashift.ui.interception.RuleAuthoringViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
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
import com.dopashift.ui.quickcreate.QuickCreateViewModel;
import com.dopashift.ui.quickcreate.QuickCreateViewModel_HiltModules;
import com.dopashift.ui.quickcreate.QuickCreateViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.dopashift.ui.quickcreate.QuickCreateViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.dopashift.ui.reminders.RemindersViewModel;
import com.dopashift.ui.reminders.RemindersViewModel_HiltModules;
import com.dopashift.ui.reminders.RemindersViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.dopashift.ui.reminders.RemindersViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.dopashift.ui.settings.AccentPickerViewModel;
import com.dopashift.ui.settings.AccentPickerViewModel_HiltModules;
import com.dopashift.ui.settings.AccentPickerViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.dopashift.ui.settings.AccentPickerViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.dopashift.ui.settings.SettingsViewModel;
import com.dopashift.ui.settings.SettingsViewModel_HiltModules;
import com.dopashift.ui.settings.SettingsViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.dopashift.ui.settings.SettingsViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.dopashift.ui.tasks.DailyTasksViewModel;
import com.dopashift.ui.tasks.DailyTasksViewModel_HiltModules;
import com.dopashift.ui.tasks.DailyTasksViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.dopashift.ui.tasks.DailyTasksViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.dopashift.ui.theme.ThemeStateViewModel;
import com.dopashift.ui.theme.ThemeStateViewModel_HiltModules;
import com.dopashift.ui.theme.ThemeStateViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.dopashift.ui.theme.ThemeStateViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
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
import java.time.ZoneId;
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
      return LazyClassKeyMap.<Boolean>of(MapBuilder.<String, Boolean>newMapBuilder(15).put(AccentPickerViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, AccentPickerViewModel_HiltModules.KeyModule.provide()).put(AnalyticsViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, AnalyticsViewModel_HiltModules.KeyModule.provide()).put(DailyTasksViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, DailyTasksViewModel_HiltModules.KeyModule.provide()).put(DashboardViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, DashboardViewModel_HiltModules.KeyModule.provide()).put(DopaShiftAppViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, DopaShiftAppViewModel_HiltModules.KeyModule.provide()).put(GoalsViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, GoalsViewModel_HiltModules.KeyModule.provide()).put(HabitRoadmapViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, HabitRoadmapViewModel_HiltModules.KeyModule.provide()).put(OnboardingViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, OnboardingViewModel_HiltModules.KeyModule.provide()).put(OverlayViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, OverlayViewModel_HiltModules.KeyModule.provide()).put(PermissionSetupViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, PermissionSetupViewModel_HiltModules.KeyModule.provide()).put(QuickCreateViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, QuickCreateViewModel_HiltModules.KeyModule.provide()).put(RemindersViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, RemindersViewModel_HiltModules.KeyModule.provide()).put(RuleAuthoringViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, RuleAuthoringViewModel_HiltModules.KeyModule.provide()).put(SettingsViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, SettingsViewModel_HiltModules.KeyModule.provide()).put(ThemeStateViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, ThemeStateViewModel_HiltModules.KeyModule.provide()).build());
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

    Provider<AccentPickerViewModel> accentPickerViewModelProvider;

    Provider<AnalyticsViewModel> analyticsViewModelProvider;

    Provider<DailyTasksViewModel> dailyTasksViewModelProvider;

    Provider<DashboardViewModel> dashboardViewModelProvider;

    Provider<DopaShiftAppViewModel> dopaShiftAppViewModelProvider;

    Provider<GoalsViewModel> goalsViewModelProvider;

    Provider<HabitRoadmapViewModel> habitRoadmapViewModelProvider;

    Provider<OnboardingViewModel> onboardingViewModelProvider;

    Provider<OverlayViewModel> overlayViewModelProvider;

    Provider<PermissionSetupViewModel> permissionSetupViewModelProvider;

    Provider<QuickCreateViewModel> quickCreateViewModelProvider;

    Provider<RemindersViewModel> remindersViewModelProvider;

    Provider<RuleAuthoringViewModel> ruleAuthoringViewModelProvider;

    Provider<SettingsViewModel> settingsViewModelProvider;

    Provider<ThemeStateViewModel> themeStateViewModelProvider;

    ViewModelCImpl(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        SavedStateHandle savedStateHandleParam, ViewModelLifecycle viewModelLifecycleParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;

      initialize(savedStateHandleParam, viewModelLifecycleParam);

    }

    @SuppressWarnings("unchecked")
    private void initialize(final SavedStateHandle savedStateHandleParam,
        final ViewModelLifecycle viewModelLifecycleParam) {
      this.accentPickerViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 0);
      this.analyticsViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 1);
      this.dailyTasksViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 2);
      this.dashboardViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 3);
      this.dopaShiftAppViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 4);
      this.goalsViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 5);
      this.habitRoadmapViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 6);
      this.onboardingViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 7);
      this.overlayViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 8);
      this.permissionSetupViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 9);
      this.quickCreateViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 10);
      this.remindersViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 11);
      this.ruleAuthoringViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 12);
      this.settingsViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 13);
      this.themeStateViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 14);
    }

    @Override
    public Map<Class<?>, javax.inject.Provider<ViewModel>> getHiltViewModelMap() {
      return LazyClassKeyMap.<javax.inject.Provider<ViewModel>>of(MapBuilder.<String, javax.inject.Provider<ViewModel>>newMapBuilder(15).put(AccentPickerViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (accentPickerViewModelProvider))).put(AnalyticsViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (analyticsViewModelProvider))).put(DailyTasksViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (dailyTasksViewModelProvider))).put(DashboardViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (dashboardViewModelProvider))).put(DopaShiftAppViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (dopaShiftAppViewModelProvider))).put(GoalsViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (goalsViewModelProvider))).put(HabitRoadmapViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (habitRoadmapViewModelProvider))).put(OnboardingViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (onboardingViewModelProvider))).put(OverlayViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (overlayViewModelProvider))).put(PermissionSetupViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (permissionSetupViewModelProvider))).put(QuickCreateViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (quickCreateViewModelProvider))).put(RemindersViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (remindersViewModelProvider))).put(RuleAuthoringViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (ruleAuthoringViewModelProvider))).put(SettingsViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (settingsViewModelProvider))).put(ThemeStateViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (themeStateViewModelProvider))).build());
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
          case 0: // com.dopashift.ui.settings.AccentPickerViewModel
          return (T) new AccentPickerViewModel(singletonCImpl.userPreferencesRepositoryImplProvider.get());

          case 1: // com.dopashift.ui.analytics.AnalyticsViewModel
          return (T) new AnalyticsViewModel(singletonCImpl.efficiencyScoreRepositoryImplProvider.get());

          case 2: // com.dopashift.ui.tasks.DailyTasksViewModel
          return (T) new DailyTasksViewModel(singletonCImpl.dailyTodoRepositoryImplProvider.get());

          case 3: // com.dopashift.ui.dashboard.DashboardViewModel
          return (T) new DashboardViewModel(singletonCImpl.goalRepositoryImplProvider.get(), singletonCImpl.dailyTodoRepositoryImplProvider.get(), singletonCImpl.habitTrackRepositoryImplProvider.get(), singletonCImpl.efficiencyScoreRepositoryImplProvider.get(), singletonCImpl.changeLogRepositoryImplProvider.get(), singletonCImpl.goalChecklistItemRepositoryImplProvider.get(), singletonCImpl.syncStatusProviderImplProvider.get());

          case 4: // com.dopashift.ui.navigation.DopaShiftAppViewModel
          return (T) new DopaShiftAppViewModel(singletonCImpl.userPreferencesRepositoryImplProvider.get());

          case 5: // com.dopashift.ui.goals.GoalsViewModel
          return (T) new GoalsViewModel(singletonCImpl.goalRepositoryImplProvider.get(), singletonCImpl.goalChecklistItemRepositoryImplProvider.get(), singletonCImpl.habitTrackRepositoryImplProvider.get());

          case 6: // com.dopashift.ui.habits.HabitRoadmapViewModel
          return (T) new HabitRoadmapViewModel(singletonCImpl.habitTrackRepositoryImplProvider.get(), singletonCImpl.goalRepositoryImplProvider.get());

          case 7: // com.dopashift.ui.onboarding.OnboardingViewModel
          return (T) new OnboardingViewModel(singletonCImpl.goalRepositoryImplProvider.get(), singletonCImpl.habitTrackRepositoryImplProvider.get(), singletonCImpl.userPreferencesRepositoryImplProvider.get());

          case 8: // com.dopashift.interception.overlay.OverlayViewModel
          return (T) new OverlayViewModel(singletonCImpl.dailyTodoRepositoryImplProvider.get(), singletonCImpl.habitTrackRepositoryImplProvider.get(), singletonCImpl.goalRepositoryImplProvider.get(), singletonCImpl.overlayTodoCompletionHandlerProvider.get(), singletonCImpl.overlayInlineGoalCaptureHandlerProvider.get());

          case 9: // com.dopashift.ui.onboarding.PermissionSetupViewModel
          return (T) new PermissionSetupViewModel(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 10: // com.dopashift.ui.quickcreate.QuickCreateViewModel
          return (T) new QuickCreateViewModel(singletonCImpl.provideCreateGoalUseCaseProvider.get(), singletonCImpl.provideCreateDailyTodoUseCaseProvider.get(), singletonCImpl.provideCreateHabitTrackUseCaseProvider.get(), singletonCImpl.provideCreateGoalWithHabitUseCaseProvider.get(), singletonCImpl.provideUndoCreationUseCaseProvider.get(), singletonCImpl.provideObserveDashboardSummaryUseCaseProvider.get(), singletonCImpl.dataStoreDiagnosticsSinkProvider.get(), singletonCImpl.assetHabitTemplateProvider.get(), singletonCImpl.assetCategoryKeywordProvider.get(), singletonCImpl.provideGoalSuggestionServiceProvider.get(), singletonCImpl.provideCorrelationIdFactoryProvider.get(), singletonCImpl.systemDomainClockProvider.get(), singletonCImpl.goalRepositoryImplProvider.get(), singletonCImpl.habitTrackRepositoryImplProvider.get(), singletonCImpl.syncStatusProviderImplProvider.get());

          case 11: // com.dopashift.ui.reminders.RemindersViewModel
          return (T) new RemindersViewModel(singletonCImpl.reminderRepositoryImplProvider.get());

          case 12: // com.dopashift.ui.interception.RuleAuthoringViewModel
          return (T) new RuleAuthoringViewModel(singletonCImpl.installedAppsProvider.get(), singletonCImpl.interceptionRuleRepositoryImplProvider.get());

          case 13: // com.dopashift.ui.settings.SettingsViewModel
          return (T) new SettingsViewModel(singletonCImpl.userPreferencesRepositoryImplProvider.get(), singletonCImpl.llmConfigRepositoryImplProvider.get(), singletonCImpl.localQuietHoursProvider.get(), singletonCImpl.llmProviderServiceImplProvider.get());

          case 14: // com.dopashift.ui.theme.ThemeStateViewModel
          return (T) new ThemeStateViewModel(singletonCImpl.userPreferencesRepositoryImplProvider.get());

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
      InterceptionService_MembersInjector.injectRepetitiveSessionTracker(instance, singletonCImpl.repetitiveSessionTrackerProvider.get());
      InterceptionService_MembersInjector.injectTodoReminderNotifier(instance, singletonCImpl.interceptionTodoReminderNotifierProvider.get());
      InterceptionService_MembersInjector.injectFocusExtensionManager(instance, singletonCImpl.focusExtensionManagerProvider.get());
      InterceptionService_MembersInjector.injectDeferredInterceptCoordinator(instance, singletonCImpl.deferredInterceptCoordinatorProvider.get());
      InterceptionService_MembersInjector.injectLimitReachedNotifier(instance, singletonCImpl.limitReachedNotifierProvider.get());
      InterceptionService_MembersInjector.injectInterceptActionAuditRepository(instance, singletonCImpl.interceptActionAuditRepositoryImplProvider.get());
      InterceptionService_MembersInjector.injectClock(instance, singletonCImpl.provideClockProvider.get());
      return instance;
    }

    private OverlayService injectOverlayService2(OverlayService instance2) {
      OverlayService_MembersInjector.injectDailyTodoRepository(instance2, singletonCImpl.dailyTodoRepositoryImplProvider.get());
      OverlayService_MembersInjector.injectHabitTrackRepository(instance2, singletonCImpl.habitTrackRepositoryImplProvider.get());
      OverlayService_MembersInjector.injectGoalRepository(instance2, singletonCImpl.goalRepositoryImplProvider.get());
      OverlayService_MembersInjector.injectTodoCompletionHandler(instance2, singletonCImpl.overlayTodoCompletionHandlerProvider.get());
      OverlayService_MembersInjector.injectInlineGoalCaptureHandler(instance2, singletonCImpl.overlayInlineGoalCaptureHandlerProvider.get());
      return instance2;
    }
  }

  private static final class SingletonCImpl extends DopaShiftApplication_HiltComponents.SingletonC {
    private final ApplicationContextModule applicationContextModule;

    private final SingletonCImpl singletonCImpl = this;

    Provider<InterceptionPermissionHelper> interceptionPermissionHelperProvider;

    Provider<PermissionRevocationDetector> permissionRevocationDetectorProvider;

    Provider<UserPreferencesRepositoryImpl> userPreferencesRepositoryImplProvider;

    Provider<DopaShiftDatabase> provideDatabaseProvider;

    Provider<EfficiencyScoreRepositoryImpl> efficiencyScoreRepositoryImplProvider;

    Provider<DailyTodoRepositoryImpl> dailyTodoRepositoryImplProvider;

    Provider<GoalRepositoryImpl> goalRepositoryImplProvider;

    Provider<HabitTrackRepositoryImpl> habitTrackRepositoryImplProvider;

    Provider<ChangeLogRepositoryImpl> changeLogRepositoryImplProvider;

    Provider<GoalChecklistItemRepositoryImpl> goalChecklistItemRepositoryImplProvider;

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

    Provider<SyncManager> provideSyncManagerProvider;

    Provider<SyncStatusProviderImpl> syncStatusProviderImplProvider;

    Provider<OverlayTodoCompletionHandler> overlayTodoCompletionHandlerProvider;

    Provider<AssetHabitTemplateProvider> assetHabitTemplateProvider;

    Provider<HabitAuthoringResolver> provideHabitAuthoringResolverProvider;

    Provider<RoomTransactionRunner> roomTransactionRunnerProvider;

    Provider<AndroidDeviceIdProvider> androidDeviceIdProvider;

    Provider<SystemDomainClock> systemDomainClockProvider;

    Provider<CreateGoalWithHabitUseCase> provideCreateGoalWithHabitUseCaseProvider;

    Provider<OverlayInlineGoalCaptureHandler> overlayInlineGoalCaptureHandlerProvider;

    Provider<CreateGoalUseCase> provideCreateGoalUseCaseProvider;

    Provider<CreateDailyTodoUseCase> provideCreateDailyTodoUseCaseProvider;

    Provider<CreateHabitTrackUseCase> provideCreateHabitTrackUseCaseProvider;

    Provider<UndoCreationUseCase> provideUndoCreationUseCaseProvider;

    Provider<QuickCreatePreferencesStore> quickCreatePreferencesStoreProvider;

    Provider<RoomDashboardSummaryRepository> roomDashboardSummaryRepositoryProvider;

    Provider<ObserveDashboardSummaryUseCase> provideObserveDashboardSummaryUseCaseProvider;

    Provider<DataStoreDiagnosticsSink> dataStoreDiagnosticsSinkProvider;

    Provider<AssetCategoryKeywordProvider> assetCategoryKeywordProvider;

    Provider<GoalSuggestionService> provideGoalSuggestionServiceProvider;

    Provider<CorrelationIdFactory> provideCorrelationIdFactoryProvider;

    Provider<ReminderRepositoryImpl> reminderRepositoryImplProvider;

    Provider<InstalledAppsProvider> installedAppsProvider;

    Provider<InterceptionRuleRepositoryImpl> interceptionRuleRepositoryImplProvider;

    Provider<LlmConfigRepositoryImpl> llmConfigRepositoryImplProvider;

    Provider<LocalQuietHoursProvider> localQuietHoursProvider;

    Provider<LlmProviderServiceImpl> llmProviderServiceImplProvider;

    Provider<DeviceCapabilityChecker> deviceCapabilityCheckerProvider;

    Provider<LocalTelemetryRepository> localTelemetryRepositoryProvider;

    Provider<Clock> provideClockProvider;

    Provider<AllowanceTracker> allowanceTrackerProvider;

    Provider<RepetitiveSessionTracker> repetitiveSessionTrackerProvider;

    Provider<InterceptionTodoReminderNotifier> interceptionTodoReminderNotifierProvider;

    Provider<ZoneId> provideZoneIdProvider;

    Provider<FocusExtensionManager> focusExtensionManagerProvider;

    Provider<SuppressionSignalSource> provideSuppressionSignalSourceProvider;

    Provider<SuppressionContextDetector> suppressionContextDetectorProvider;

    Provider<DeferredInterceptCoordinator> deferredInterceptCoordinatorProvider;

    Provider<LimitReachedNotifier> limitReachedNotifierProvider;

    Provider<InterceptActionAuditRepositoryImpl> interceptActionAuditRepositoryImplProvider;

    SingletonCImpl(ApplicationContextModule applicationContextModuleParam) {
      this.applicationContextModule = applicationContextModuleParam;
      initialize(applicationContextModuleParam);
      initialize2(applicationContextModuleParam);
      initialize3(applicationContextModuleParam);

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

    SyncStateDao syncStateDao() {
      return DataModule_ProvideSyncStateDaoFactory.provideSyncStateDao(provideDatabaseProvider.get());
    }

    DiagnosticEventDao diagnosticEventDao() {
      return DataModule_ProvideDiagnosticEventDaoFactory.provideDiagnosticEventDao(provideDatabaseProvider.get());
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

    InterceptActionAuditDao interceptActionAuditDao() {
      return DataModule_ProvideInterceptActionAuditDaoFactory.provideInterceptActionAuditDao(provideDatabaseProvider.get());
    }

    @SuppressWarnings("unchecked")
    private void initialize(final ApplicationContextModule applicationContextModuleParam) {
      this.interceptionPermissionHelperProvider = DoubleCheck.provider(new SwitchingProvider<InterceptionPermissionHelper>(singletonCImpl, 1));
      this.permissionRevocationDetectorProvider = DoubleCheck.provider(new SwitchingProvider<PermissionRevocationDetector>(singletonCImpl, 0));
      this.userPreferencesRepositoryImplProvider = DoubleCheck.provider(new SwitchingProvider<UserPreferencesRepositoryImpl>(singletonCImpl, 2));
      this.provideDatabaseProvider = DoubleCheck.provider(new SwitchingProvider<DopaShiftDatabase>(singletonCImpl, 4));
      this.efficiencyScoreRepositoryImplProvider = DoubleCheck.provider(new SwitchingProvider<EfficiencyScoreRepositoryImpl>(singletonCImpl, 3));
      this.dailyTodoRepositoryImplProvider = DoubleCheck.provider(new SwitchingProvider<DailyTodoRepositoryImpl>(singletonCImpl, 5));
      this.goalRepositoryImplProvider = DoubleCheck.provider(new SwitchingProvider<GoalRepositoryImpl>(singletonCImpl, 6));
      this.habitTrackRepositoryImplProvider = DoubleCheck.provider(new SwitchingProvider<HabitTrackRepositoryImpl>(singletonCImpl, 7));
      this.changeLogRepositoryImplProvider = DoubleCheck.provider(new SwitchingProvider<ChangeLogRepositoryImpl>(singletonCImpl, 8));
      this.goalChecklistItemRepositoryImplProvider = DoubleCheck.provider(new SwitchingProvider<GoalChecklistItemRepositoryImpl>(singletonCImpl, 9));
      this.provideAuthDataStoreProvider = DoubleCheck.provider(new SwitchingProvider<DataStore<Preferences>>(singletonCImpl, 17));
      this.provideAuthOkHttpClientProvider = DoubleCheck.provider(new SwitchingProvider<OkHttpClient>(singletonCImpl, 20));
      this.provideMoshiProvider = DoubleCheck.provider(new SwitchingProvider<Moshi>(singletonCImpl, 21));
      this.provideAuthRetrofitProvider = DoubleCheck.provider(new SwitchingProvider<Retrofit>(singletonCImpl, 19));
      this.provideAuthApiProvider = DoubleCheck.provider(new SwitchingProvider<AuthApiProvider>(singletonCImpl, 18));
      this.provideTokenManagerProvider = DoubleCheck.provider(new SwitchingProvider<TokenManager>(singletonCImpl, 16));
      this.authInterceptorProvider = DoubleCheck.provider(new SwitchingProvider<AuthInterceptor>(singletonCImpl, 15));
      this.provideOkHttpClientProvider = DoubleCheck.provider(new SwitchingProvider<OkHttpClient>(singletonCImpl, 14));
      this.provideRetrofitProvider = DoubleCheck.provider(new SwitchingProvider<Retrofit>(singletonCImpl, 13));
      this.provideDopaShiftApiProvider = DoubleCheck.provider(new SwitchingProvider<DopaShiftApi>(singletonCImpl, 12));
      this.provideSyncManagerProvider = DoubleCheck.provider(new SwitchingProvider<SyncManager>(singletonCImpl, 11));
      this.syncStatusProviderImplProvider = DoubleCheck.provider(new SwitchingProvider<SyncStatusProviderImpl>(singletonCImpl, 10));
      this.overlayTodoCompletionHandlerProvider = DoubleCheck.provider(new SwitchingProvider<OverlayTodoCompletionHandler>(singletonCImpl, 22));
      this.assetHabitTemplateProvider = DoubleCheck.provider(new SwitchingProvider<AssetHabitTemplateProvider>(singletonCImpl, 26));
      this.provideHabitAuthoringResolverProvider = DoubleCheck.provider(new SwitchingProvider<HabitAuthoringResolver>(singletonCImpl, 25));
    }

    @SuppressWarnings("unchecked")
    private void initialize2(final ApplicationContextModule applicationContextModuleParam) {
      this.roomTransactionRunnerProvider = DoubleCheck.provider(new SwitchingProvider<RoomTransactionRunner>(singletonCImpl, 27));
      this.androidDeviceIdProvider = DoubleCheck.provider(new SwitchingProvider<AndroidDeviceIdProvider>(singletonCImpl, 28));
      this.systemDomainClockProvider = DoubleCheck.provider(new SwitchingProvider<SystemDomainClock>(singletonCImpl, 29));
      this.provideCreateGoalWithHabitUseCaseProvider = DoubleCheck.provider(new SwitchingProvider<CreateGoalWithHabitUseCase>(singletonCImpl, 24));
      this.overlayInlineGoalCaptureHandlerProvider = DoubleCheck.provider(new SwitchingProvider<OverlayInlineGoalCaptureHandler>(singletonCImpl, 23));
      this.provideCreateGoalUseCaseProvider = DoubleCheck.provider(new SwitchingProvider<CreateGoalUseCase>(singletonCImpl, 30));
      this.provideCreateDailyTodoUseCaseProvider = DoubleCheck.provider(new SwitchingProvider<CreateDailyTodoUseCase>(singletonCImpl, 31));
      this.provideCreateHabitTrackUseCaseProvider = DoubleCheck.provider(new SwitchingProvider<CreateHabitTrackUseCase>(singletonCImpl, 32));
      this.provideUndoCreationUseCaseProvider = DoubleCheck.provider(new SwitchingProvider<UndoCreationUseCase>(singletonCImpl, 33));
      this.quickCreatePreferencesStoreProvider = DoubleCheck.provider(new SwitchingProvider<QuickCreatePreferencesStore>(singletonCImpl, 36));
      this.roomDashboardSummaryRepositoryProvider = DoubleCheck.provider(new SwitchingProvider<RoomDashboardSummaryRepository>(singletonCImpl, 35));
      this.provideObserveDashboardSummaryUseCaseProvider = DoubleCheck.provider(new SwitchingProvider<ObserveDashboardSummaryUseCase>(singletonCImpl, 34));
      this.dataStoreDiagnosticsSinkProvider = DoubleCheck.provider(new SwitchingProvider<DataStoreDiagnosticsSink>(singletonCImpl, 37));
      this.assetCategoryKeywordProvider = DoubleCheck.provider(new SwitchingProvider<AssetCategoryKeywordProvider>(singletonCImpl, 38));
      this.provideGoalSuggestionServiceProvider = DoubleCheck.provider(new SwitchingProvider<GoalSuggestionService>(singletonCImpl, 39));
      this.provideCorrelationIdFactoryProvider = DoubleCheck.provider(new SwitchingProvider<CorrelationIdFactory>(singletonCImpl, 40));
      this.reminderRepositoryImplProvider = DoubleCheck.provider(new SwitchingProvider<ReminderRepositoryImpl>(singletonCImpl, 41));
      this.installedAppsProvider = DoubleCheck.provider(new SwitchingProvider<InstalledAppsProvider>(singletonCImpl, 42));
      this.interceptionRuleRepositoryImplProvider = DoubleCheck.provider(new SwitchingProvider<InterceptionRuleRepositoryImpl>(singletonCImpl, 43));
      this.llmConfigRepositoryImplProvider = DoubleCheck.provider(new SwitchingProvider<LlmConfigRepositoryImpl>(singletonCImpl, 44));
      this.localQuietHoursProvider = DoubleCheck.provider(new SwitchingProvider<LocalQuietHoursProvider>(singletonCImpl, 45));
      this.llmProviderServiceImplProvider = DoubleCheck.provider(new SwitchingProvider<LlmProviderServiceImpl>(singletonCImpl, 46));
      this.deviceCapabilityCheckerProvider = DoubleCheck.provider(new SwitchingProvider<DeviceCapabilityChecker>(singletonCImpl, 47));
      this.localTelemetryRepositoryProvider = DoubleCheck.provider(new SwitchingProvider<LocalTelemetryRepository>(singletonCImpl, 49));
      this.provideClockProvider = DoubleCheck.provider(new SwitchingProvider<Clock>(singletonCImpl, 50));
    }

    @SuppressWarnings("unchecked")
    private void initialize3(final ApplicationContextModule applicationContextModuleParam) {
      this.allowanceTrackerProvider = DoubleCheck.provider(new SwitchingProvider<AllowanceTracker>(singletonCImpl, 48));
      this.repetitiveSessionTrackerProvider = DoubleCheck.provider(new SwitchingProvider<RepetitiveSessionTracker>(singletonCImpl, 51));
      this.interceptionTodoReminderNotifierProvider = DoubleCheck.provider(new SwitchingProvider<InterceptionTodoReminderNotifier>(singletonCImpl, 52));
      this.provideZoneIdProvider = DoubleCheck.provider(new SwitchingProvider<ZoneId>(singletonCImpl, 54));
      this.focusExtensionManagerProvider = DoubleCheck.provider(new SwitchingProvider<FocusExtensionManager>(singletonCImpl, 53));
      this.provideSuppressionSignalSourceProvider = DoubleCheck.provider(new SwitchingProvider<SuppressionSignalSource>(singletonCImpl, 57));
      this.suppressionContextDetectorProvider = DoubleCheck.provider(new SwitchingProvider<SuppressionContextDetector>(singletonCImpl, 56));
      this.deferredInterceptCoordinatorProvider = DoubleCheck.provider(new SwitchingProvider<DeferredInterceptCoordinator>(singletonCImpl, 55));
      this.limitReachedNotifierProvider = DoubleCheck.provider(new SwitchingProvider<LimitReachedNotifier>(singletonCImpl, 58));
      this.interceptActionAuditRepositoryImplProvider = DoubleCheck.provider(new SwitchingProvider<InterceptActionAuditRepositoryImpl>(singletonCImpl, 59));
    }

    @Override
    public void injectDopaShiftApplication(DopaShiftApplication dopaShiftApplication) {
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

          case 2: // com.dopashift.data.repository.UserPreferencesRepositoryImpl
          return (T) new UserPreferencesRepositoryImpl(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 3: // com.dopashift.data.repository.EfficiencyScoreRepositoryImpl
          return (T) new EfficiencyScoreRepositoryImpl(singletonCImpl.efficiencyScoreDao());

          case 4: // com.dopashift.data.local.DopaShiftDatabase
          return (T) DataModule_ProvideDatabaseFactory.provideDatabase(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 5: // com.dopashift.data.repository.DailyTodoRepositoryImpl
          return (T) new DailyTodoRepositoryImpl(singletonCImpl.dailyTodoDao());

          case 6: // com.dopashift.data.repository.GoalRepositoryImpl
          return (T) new GoalRepositoryImpl(singletonCImpl.goalDao());

          case 7: // com.dopashift.data.repository.HabitTrackRepositoryImpl
          return (T) new HabitTrackRepositoryImpl(singletonCImpl.habitTrackDao());

          case 8: // com.dopashift.data.repository.ChangeLogRepositoryImpl
          return (T) new ChangeLogRepositoryImpl(singletonCImpl.changeLogDao());

          case 9: // com.dopashift.data.repository.GoalChecklistItemRepositoryImpl
          return (T) new GoalChecklistItemRepositoryImpl(singletonCImpl.goalChecklistDao());

          case 10: // com.dopashift.sync.SyncStatusProviderImpl
          return (T) new SyncStatusProviderImpl(singletonCImpl.provideSyncManagerProvider.get());

          case 11: // com.dopashift.sync.SyncManager
          return (T) SyncModule_ProvideSyncManagerFactory.provideSyncManager(singletonCImpl.changeLogDao(), singletonCImpl.syncStateDao(), singletonCImpl.provideDopaShiftApiProvider.get());

          case 12: // com.dopashift.data.remote.DopaShiftApi
          return (T) NetworkModule_ProvideDopaShiftApiFactory.provideDopaShiftApi(singletonCImpl.provideRetrofitProvider.get());

          case 13: // retrofit2.Retrofit
          return (T) NetworkModule_ProvideRetrofitFactory.provideRetrofit(singletonCImpl.provideOkHttpClientProvider.get(), singletonCImpl.provideMoshiProvider.get());

          case 14: // okhttp3.OkHttpClient
          return (T) NetworkModule_ProvideOkHttpClientFactory.provideOkHttpClient(singletonCImpl.authInterceptorProvider.get());

          case 15: // com.dopashift.data.remote.AuthInterceptor
          return (T) new AuthInterceptor(singletonCImpl.provideTokenManagerProvider.get());

          case 16: // com.dopashift.data.remote.TokenManager
          return (T) NetworkModule_ProvideTokenManagerFactory.provideTokenManager(singletonCImpl.provideAuthDataStoreProvider.get(), singletonCImpl.provideAuthApiProvider.get());

          case 17: // androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>
          return (T) NetworkModule_ProvideAuthDataStoreFactory.provideAuthDataStore(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 18: // com.dopashift.data.remote.AuthApiProvider
          return (T) NetworkModule_ProvideAuthApiProviderFactory.provideAuthApiProvider(singletonCImpl.provideAuthRetrofitProvider.get());

          case 19: // @com.dopashift.data.di.AuthClient retrofit2.Retrofit
          return (T) NetworkModule_ProvideAuthRetrofitFactory.provideAuthRetrofit(singletonCImpl.provideAuthOkHttpClientProvider.get(), singletonCImpl.provideMoshiProvider.get());

          case 20: // @com.dopashift.data.di.AuthClient okhttp3.OkHttpClient
          return (T) NetworkModule_ProvideAuthOkHttpClientFactory.provideAuthOkHttpClient();

          case 21: // com.squareup.moshi.Moshi
          return (T) NetworkModule_ProvideMoshiFactory.provideMoshi();

          case 22: // com.dopashift.interception.overlay.OverlayTodoCompletionHandler
          return (T) new OverlayTodoCompletionHandler(singletonCImpl.dailyTodoRepositoryImplProvider.get(), singletonCImpl.changeLogRepositoryImplProvider.get());

          case 23: // com.dopashift.interception.overlay.OverlayInlineGoalCaptureHandler
          return (T) new OverlayInlineGoalCaptureHandler(singletonCImpl.provideCreateGoalWithHabitUseCaseProvider.get());

          case 24: // com.dopashift.domain.creation.usecase.CreateGoalWithHabitUseCase
          return (T) QuickCreateUseCaseModule_ProvideCreateGoalWithHabitUseCaseFactory.provideCreateGoalWithHabitUseCase(singletonCImpl.goalRepositoryImplProvider.get(), singletonCImpl.habitTrackRepositoryImplProvider.get(), singletonCImpl.changeLogRepositoryImplProvider.get(), singletonCImpl.provideHabitAuthoringResolverProvider.get(), singletonCImpl.roomTransactionRunnerProvider.get(), singletonCImpl.androidDeviceIdProvider.get(), singletonCImpl.systemDomainClockProvider.get());

          case 25: // com.dopashift.domain.creation.HabitAuthoringResolver
          return (T) QuickCreateUseCaseModule_ProvideHabitAuthoringResolverFactory.provideHabitAuthoringResolver(singletonCImpl.assetHabitTemplateProvider.get());

          case 26: // com.dopashift.data.asset.AssetHabitTemplateProvider
          return (T) new AssetHabitTemplateProvider(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule), singletonCImpl.provideMoshiProvider.get());

          case 27: // com.dopashift.data.repository.RoomTransactionRunner
          return (T) new RoomTransactionRunner(singletonCImpl.provideDatabaseProvider.get());

          case 28: // com.dopashift.app.creation.AndroidDeviceIdProvider
          return (T) new AndroidDeviceIdProvider(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 29: // com.dopashift.app.creation.SystemDomainClock
          return (T) new SystemDomainClock();

          case 30: // com.dopashift.domain.creation.usecase.CreateGoalUseCase
          return (T) QuickCreateUseCaseModule_ProvideCreateGoalUseCaseFactory.provideCreateGoalUseCase(singletonCImpl.goalRepositoryImplProvider.get(), singletonCImpl.changeLogRepositoryImplProvider.get(), singletonCImpl.androidDeviceIdProvider.get(), singletonCImpl.systemDomainClockProvider.get());

          case 31: // com.dopashift.domain.creation.usecase.CreateDailyTodoUseCase
          return (T) QuickCreateUseCaseModule_ProvideCreateDailyTodoUseCaseFactory.provideCreateDailyTodoUseCase(singletonCImpl.dailyTodoRepositoryImplProvider.get(), singletonCImpl.changeLogRepositoryImplProvider.get(), singletonCImpl.androidDeviceIdProvider.get(), singletonCImpl.systemDomainClockProvider.get());

          case 32: // com.dopashift.domain.creation.usecase.CreateHabitTrackUseCase
          return (T) QuickCreateUseCaseModule_ProvideCreateHabitTrackUseCaseFactory.provideCreateHabitTrackUseCase(singletonCImpl.goalRepositoryImplProvider.get(), singletonCImpl.habitTrackRepositoryImplProvider.get(), singletonCImpl.changeLogRepositoryImplProvider.get(), singletonCImpl.provideHabitAuthoringResolverProvider.get(), singletonCImpl.androidDeviceIdProvider.get(), singletonCImpl.systemDomainClockProvider.get());

          case 33: // com.dopashift.domain.creation.usecase.UndoCreationUseCase
          return (T) QuickCreateUseCaseModule_ProvideUndoCreationUseCaseFactory.provideUndoCreationUseCase(singletonCImpl.goalRepositoryImplProvider.get(), singletonCImpl.habitTrackRepositoryImplProvider.get(), singletonCImpl.dailyTodoRepositoryImplProvider.get(), singletonCImpl.changeLogRepositoryImplProvider.get(), singletonCImpl.androidDeviceIdProvider.get(), singletonCImpl.systemDomainClockProvider.get());

          case 34: // com.dopashift.domain.creation.usecase.ObserveDashboardSummaryUseCase
          return (T) QuickCreateUseCaseModule_ProvideObserveDashboardSummaryUseCaseFactory.provideObserveDashboardSummaryUseCase(singletonCImpl.roomDashboardSummaryRepositoryProvider.get());

          case 35: // com.dopashift.data.repository.RoomDashboardSummaryRepository
          return (T) new RoomDashboardSummaryRepository(singletonCImpl.goalDao(), singletonCImpl.dailyTodoDao(), singletonCImpl.habitTrackDao(), singletonCImpl.quickCreatePreferencesStoreProvider.get(), singletonCImpl.provideDopaShiftApiProvider.get());

          case 36: // com.dopashift.data.repository.QuickCreatePreferencesStore
          return (T) new QuickCreatePreferencesStore(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 37: // com.dopashift.data.repository.DataStoreDiagnosticsSink
          return (T) new DataStoreDiagnosticsSink(singletonCImpl.diagnosticEventDao());

          case 38: // com.dopashift.data.asset.AssetCategoryKeywordProvider
          return (T) new AssetCategoryKeywordProvider(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule), singletonCImpl.provideMoshiProvider.get());

          case 39: // com.dopashift.domain.creation.GoalSuggestionService
          return (T) QuickCreateUseCaseModule_ProvideGoalSuggestionServiceFactory.provideGoalSuggestionService();

          case 40: // com.dopashift.domain.creation.CorrelationIdFactory
          return (T) QuickCreateUseCaseModule_ProvideCorrelationIdFactoryFactory.provideCorrelationIdFactory();

          case 41: // com.dopashift.data.repository.ReminderRepositoryImpl
          return (T) new ReminderRepositoryImpl(singletonCImpl.reminderDao());

          case 42: // com.dopashift.ui.interception.InstalledAppsProvider
          return (T) new InstalledAppsProvider(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 43: // com.dopashift.data.repository.InterceptionRuleRepositoryImpl
          return (T) new InterceptionRuleRepositoryImpl(singletonCImpl.interceptionRuleDao());

          case 44: // com.dopashift.data.repository.LlmConfigRepositoryImpl
          return (T) new LlmConfigRepositoryImpl(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 45: // com.dopashift.data.repository.LocalQuietHoursProvider
          return (T) new LocalQuietHoursProvider(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 46: // com.dopashift.data.remote.LlmProviderServiceImpl
          return (T) new LlmProviderServiceImpl(singletonCImpl.provideDopaShiftApiProvider.get());

          case 47: // com.dopashift.interception.DeviceCapabilityChecker
          return (T) new DeviceCapabilityChecker();

          case 48: // com.dopashift.interception.AllowanceTracker
          return (T) new AllowanceTracker(singletonCImpl.interceptionRuleRepositoryImplProvider.get(), singletonCImpl.localTelemetryRepositoryProvider.get(), singletonCImpl.provideClockProvider.get());

          case 49: // com.dopashift.data.repository.LocalTelemetryRepository
          return (T) new LocalTelemetryRepository(singletonCImpl.telemetryDao());

          case 50: // java.time.Clock
          return (T) ReminderModule_Companion_ProvideClockFactory.provideClock();

          case 51: // com.dopashift.interception.RepetitiveSessionTracker
          return (T) new RepetitiveSessionTracker(singletonCImpl.interceptionRuleRepositoryImplProvider.get());

          case 52: // com.dopashift.interception.reminder.InterceptionTodoReminderNotifier
          return (T) new InterceptionTodoReminderNotifier(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule), singletonCImpl.dailyTodoRepositoryImplProvider.get());

          case 53: // com.dopashift.interception.FocusExtensionManager
          return (T) new FocusExtensionManager(singletonCImpl.provideClockProvider.get(), singletonCImpl.provideZoneIdProvider.get());

          case 54: // java.time.ZoneId
          return (T) InterceptionModule_ProvideZoneIdFactory.provideZoneId();

          case 55: // com.dopashift.interception.DeferredInterceptCoordinator
          return (T) new DeferredInterceptCoordinator(singletonCImpl.suppressionContextDetectorProvider.get(), singletonCImpl.focusExtensionManagerProvider.get(), singletonCImpl.provideClockProvider.get());

          case 56: // com.dopashift.interception.SuppressionContextDetector
          return (T) new SuppressionContextDetector(singletonCImpl.provideSuppressionSignalSourceProvider.get());

          case 57: // com.dopashift.interception.SuppressionSignalSource
          return (T) InterceptionModule_ProvideSuppressionSignalSourceFactory.provideSuppressionSignalSource(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 58: // com.dopashift.interception.reminder.LimitReachedNotifier
          return (T) new LimitReachedNotifier(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 59: // com.dopashift.data.repository.InterceptActionAuditRepositoryImpl
          return (T) new InterceptActionAuditRepositoryImpl(singletonCImpl.interceptActionAuditDao());

          default: throw new AssertionError(id);
        }
      }
    }
  }
}
