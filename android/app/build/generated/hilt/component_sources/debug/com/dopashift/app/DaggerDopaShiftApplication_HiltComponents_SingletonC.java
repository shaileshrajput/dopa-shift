package com.dopashift.app;

import android.app.Activity;
import android.app.Service;
import android.view.View;
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
import com.dopashift.data.di.DataModule_ProvideReminderDaoFactory;
import com.dopashift.data.local.DopaShiftDatabase;
import com.dopashift.data.local.dao.ChangeLogDao;
import com.dopashift.data.local.dao.DailyTodoDao;
import com.dopashift.data.local.dao.EfficiencyScoreDao;
import com.dopashift.data.local.dao.GoalChecklistDao;
import com.dopashift.data.local.dao.GoalDao;
import com.dopashift.data.local.dao.HabitTrackDao;
import com.dopashift.data.local.dao.ReminderDao;
import com.dopashift.data.repository.ChangeLogRepositoryImpl;
import com.dopashift.data.repository.DailyTodoRepositoryImpl;
import com.dopashift.data.repository.EfficiencyScoreRepositoryImpl;
import com.dopashift.data.repository.GoalChecklistItemRepositoryImpl;
import com.dopashift.data.repository.GoalRepositoryImpl;
import com.dopashift.data.repository.HabitTrackRepositoryImpl;
import com.dopashift.data.repository.ReminderRepositoryImpl;
import com.dopashift.data.repository.UserPreferencesRepositoryImpl;
import com.dopashift.interception.DeviceCapabilityChecker;
import com.dopashift.interception.InterceptionPermissionHelper;
import com.dopashift.interception.InterceptionService;
import com.dopashift.interception.InterceptionService_MembersInjector;
import com.dopashift.interception.overlay.OverlayService;
import com.dopashift.interception.overlay.OverlayService_MembersInjector;
import com.dopashift.interception.overlay.OverlayViewModel;
import com.dopashift.interception.overlay.OverlayViewModel_HiltModules_KeyModule_ProvideFactory;
import com.dopashift.ui.analytics.AnalyticsViewModel;
import com.dopashift.ui.analytics.AnalyticsViewModel_HiltModules_KeyModule_ProvideFactory;
import com.dopashift.ui.dashboard.DashboardViewModel;
import com.dopashift.ui.dashboard.DashboardViewModel_HiltModules_KeyModule_ProvideFactory;
import com.dopashift.ui.goals.GoalsViewModel;
import com.dopashift.ui.goals.GoalsViewModel_HiltModules_KeyModule_ProvideFactory;
import com.dopashift.ui.habits.HabitRoadmapViewModel;
import com.dopashift.ui.habits.HabitRoadmapViewModel_HiltModules_KeyModule_ProvideFactory;
import com.dopashift.ui.onboarding.OnboardingViewModel;
import com.dopashift.ui.onboarding.OnboardingViewModel_HiltModules_KeyModule_ProvideFactory;
import com.dopashift.ui.reminders.RemindersViewModel;
import com.dopashift.ui.reminders.RemindersViewModel_HiltModules_KeyModule_ProvideFactory;
import com.dopashift.ui.settings.SettingsViewModel;
import com.dopashift.ui.settings.SettingsViewModel_HiltModules_KeyModule_ProvideFactory;
import com.dopashift.ui.tasks.DailyTasksViewModel;
import com.dopashift.ui.tasks.DailyTasksViewModel_HiltModules_KeyModule_ProvideFactory;
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
import dagger.internal.MapBuilder;
import dagger.internal.Preconditions;
import dagger.internal.Provider;
import dagger.internal.SetBuilder;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

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

    private ViewWithFragmentCImpl(SingletonCImpl singletonCImpl,
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

    private FragmentCImpl(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl,
        Fragment fragmentParam) {
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

    private ViewCImpl(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
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

    private ActivityCImpl(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, Activity activityParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;


    }

    @Override
    public void injectMainActivity(MainActivity arg0) {
    }

    @Override
    public DefaultViewModelFactories.InternalFactoryFactory getHiltInternalFactoryFactory() {
      return DefaultViewModelFactories_InternalFactoryFactory_Factory.newInstance(getViewModelKeys(), new ViewModelCBuilder(singletonCImpl, activityRetainedCImpl));
    }

    @Override
    public Set<String> getViewModelKeys() {
      return SetBuilder.<String>newSetBuilder(9).add(AnalyticsViewModel_HiltModules_KeyModule_ProvideFactory.provide()).add(DailyTasksViewModel_HiltModules_KeyModule_ProvideFactory.provide()).add(DashboardViewModel_HiltModules_KeyModule_ProvideFactory.provide()).add(GoalsViewModel_HiltModules_KeyModule_ProvideFactory.provide()).add(HabitRoadmapViewModel_HiltModules_KeyModule_ProvideFactory.provide()).add(OnboardingViewModel_HiltModules_KeyModule_ProvideFactory.provide()).add(OverlayViewModel_HiltModules_KeyModule_ProvideFactory.provide()).add(RemindersViewModel_HiltModules_KeyModule_ProvideFactory.provide()).add(SettingsViewModel_HiltModules_KeyModule_ProvideFactory.provide()).build();
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
  }

  private static final class ViewModelCImpl extends DopaShiftApplication_HiltComponents.ViewModelC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ViewModelCImpl viewModelCImpl = this;

    private Provider<AnalyticsViewModel> analyticsViewModelProvider;

    private Provider<DailyTasksViewModel> dailyTasksViewModelProvider;

    private Provider<DashboardViewModel> dashboardViewModelProvider;

    private Provider<GoalsViewModel> goalsViewModelProvider;

    private Provider<HabitRoadmapViewModel> habitRoadmapViewModelProvider;

    private Provider<OnboardingViewModel> onboardingViewModelProvider;

    private Provider<OverlayViewModel> overlayViewModelProvider;

    private Provider<RemindersViewModel> remindersViewModelProvider;

    private Provider<SettingsViewModel> settingsViewModelProvider;

    private ViewModelCImpl(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, SavedStateHandle savedStateHandleParam,
        ViewModelLifecycle viewModelLifecycleParam) {
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
      this.goalsViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 3);
      this.habitRoadmapViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 4);
      this.onboardingViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 5);
      this.overlayViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 6);
      this.remindersViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 7);
      this.settingsViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 8);
    }

    @Override
    public Map<String, javax.inject.Provider<ViewModel>> getHiltViewModelMap() {
      return MapBuilder.<String, javax.inject.Provider<ViewModel>>newMapBuilder(9).put("com.dopashift.ui.analytics.AnalyticsViewModel", ((Provider) analyticsViewModelProvider)).put("com.dopashift.ui.tasks.DailyTasksViewModel", ((Provider) dailyTasksViewModelProvider)).put("com.dopashift.ui.dashboard.DashboardViewModel", ((Provider) dashboardViewModelProvider)).put("com.dopashift.ui.goals.GoalsViewModel", ((Provider) goalsViewModelProvider)).put("com.dopashift.ui.habits.HabitRoadmapViewModel", ((Provider) habitRoadmapViewModelProvider)).put("com.dopashift.ui.onboarding.OnboardingViewModel", ((Provider) onboardingViewModelProvider)).put("com.dopashift.interception.overlay.OverlayViewModel", ((Provider) overlayViewModelProvider)).put("com.dopashift.ui.reminders.RemindersViewModel", ((Provider) remindersViewModelProvider)).put("com.dopashift.ui.settings.SettingsViewModel", ((Provider) settingsViewModelProvider)).build();
    }

    @Override
    public Map<String, Object> getHiltViewModelAssistedMap() {
      return Collections.<String, Object>emptyMap();
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

      @SuppressWarnings("unchecked")
      @Override
      public T get() {
        switch (id) {
          case 0: // com.dopashift.ui.analytics.AnalyticsViewModel 
          return (T) new AnalyticsViewModel(singletonCImpl.efficiencyScoreRepositoryImplProvider.get());

          case 1: // com.dopashift.ui.tasks.DailyTasksViewModel 
          return (T) new DailyTasksViewModel(singletonCImpl.dailyTodoRepositoryImplProvider.get());

          case 2: // com.dopashift.ui.dashboard.DashboardViewModel 
          return (T) new DashboardViewModel(singletonCImpl.goalRepositoryImplProvider.get(), singletonCImpl.dailyTodoRepositoryImplProvider.get(), singletonCImpl.habitTrackRepositoryImplProvider.get(), singletonCImpl.efficiencyScoreRepositoryImplProvider.get(), singletonCImpl.changeLogRepositoryImplProvider.get(), singletonCImpl.goalChecklistItemRepositoryImplProvider.get());

          case 3: // com.dopashift.ui.goals.GoalsViewModel 
          return (T) new GoalsViewModel(singletonCImpl.goalRepositoryImplProvider.get(), singletonCImpl.goalChecklistItemRepositoryImplProvider.get(), singletonCImpl.habitTrackRepositoryImplProvider.get());

          case 4: // com.dopashift.ui.habits.HabitRoadmapViewModel 
          return (T) new HabitRoadmapViewModel(singletonCImpl.habitTrackRepositoryImplProvider.get(), singletonCImpl.goalRepositoryImplProvider.get());

          case 5: // com.dopashift.ui.onboarding.OnboardingViewModel 
          return (T) new OnboardingViewModel(singletonCImpl.goalRepositoryImplProvider.get(), singletonCImpl.habitTrackRepositoryImplProvider.get(), singletonCImpl.userPreferencesRepositoryImplProvider.get());

          case 6: // com.dopashift.interception.overlay.OverlayViewModel 
          return (T) new OverlayViewModel(singletonCImpl.dailyTodoRepositoryImplProvider.get(), singletonCImpl.habitTrackRepositoryImplProvider.get());

          case 7: // com.dopashift.ui.reminders.RemindersViewModel 
          return (T) new RemindersViewModel(singletonCImpl.reminderRepositoryImplProvider.get());

          case 8: // com.dopashift.ui.settings.SettingsViewModel 
          return (T) new SettingsViewModel();

          default: throw new AssertionError(id);
        }
      }
    }
  }

  private static final class ActivityRetainedCImpl extends DopaShiftApplication_HiltComponents.ActivityRetainedC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl = this;

    private Provider<ActivityRetainedLifecycle> provideActivityRetainedLifecycleProvider;

    private ActivityRetainedCImpl(SingletonCImpl singletonCImpl,
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

      @SuppressWarnings("unchecked")
      @Override
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

    private ServiceCImpl(SingletonCImpl singletonCImpl, Service serviceParam) {
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
      return instance;
    }

    private OverlayService injectOverlayService2(OverlayService instance) {
      OverlayService_MembersInjector.injectDailyTodoRepository(instance, singletonCImpl.dailyTodoRepositoryImplProvider.get());
      OverlayService_MembersInjector.injectHabitTrackRepository(instance, singletonCImpl.habitTrackRepositoryImplProvider.get());
      return instance;
    }
  }

  private static final class SingletonCImpl extends DopaShiftApplication_HiltComponents.SingletonC {
    private final ApplicationContextModule applicationContextModule;

    private final SingletonCImpl singletonCImpl = this;

    private Provider<DopaShiftDatabase> provideDatabaseProvider;

    private Provider<EfficiencyScoreRepositoryImpl> efficiencyScoreRepositoryImplProvider;

    private Provider<DailyTodoRepositoryImpl> dailyTodoRepositoryImplProvider;

    private Provider<GoalRepositoryImpl> goalRepositoryImplProvider;

    private Provider<HabitTrackRepositoryImpl> habitTrackRepositoryImplProvider;

    private Provider<ChangeLogRepositoryImpl> changeLogRepositoryImplProvider;

    private Provider<GoalChecklistItemRepositoryImpl> goalChecklistItemRepositoryImplProvider;

    private Provider<UserPreferencesRepositoryImpl> userPreferencesRepositoryImplProvider;

    private Provider<ReminderRepositoryImpl> reminderRepositoryImplProvider;

    private Provider<InterceptionPermissionHelper> interceptionPermissionHelperProvider;

    private Provider<DeviceCapabilityChecker> deviceCapabilityCheckerProvider;

    private SingletonCImpl(ApplicationContextModule applicationContextModuleParam) {
      this.applicationContextModule = applicationContextModuleParam;
      initialize(applicationContextModuleParam);

    }

    private EfficiencyScoreDao efficiencyScoreDao() {
      return DataModule_ProvideEfficiencyScoreDaoFactory.provideEfficiencyScoreDao(provideDatabaseProvider.get());
    }

    private DailyTodoDao dailyTodoDao() {
      return DataModule_ProvideDailyTodoDaoFactory.provideDailyTodoDao(provideDatabaseProvider.get());
    }

    private GoalDao goalDao() {
      return DataModule_ProvideGoalDaoFactory.provideGoalDao(provideDatabaseProvider.get());
    }

    private HabitTrackDao habitTrackDao() {
      return DataModule_ProvideHabitTrackDaoFactory.provideHabitTrackDao(provideDatabaseProvider.get());
    }

    private ChangeLogDao changeLogDao() {
      return DataModule_ProvideChangeLogDaoFactory.provideChangeLogDao(provideDatabaseProvider.get());
    }

    private GoalChecklistDao goalChecklistDao() {
      return DataModule_ProvideGoalChecklistDaoFactory.provideGoalChecklistDao(provideDatabaseProvider.get());
    }

    private ReminderDao reminderDao() {
      return DataModule_ProvideReminderDaoFactory.provideReminderDao(provideDatabaseProvider.get());
    }

    @SuppressWarnings("unchecked")
    private void initialize(final ApplicationContextModule applicationContextModuleParam) {
      this.provideDatabaseProvider = DoubleCheck.provider(new SwitchingProvider<DopaShiftDatabase>(singletonCImpl, 1));
      this.efficiencyScoreRepositoryImplProvider = DoubleCheck.provider(new SwitchingProvider<EfficiencyScoreRepositoryImpl>(singletonCImpl, 0));
      this.dailyTodoRepositoryImplProvider = DoubleCheck.provider(new SwitchingProvider<DailyTodoRepositoryImpl>(singletonCImpl, 2));
      this.goalRepositoryImplProvider = DoubleCheck.provider(new SwitchingProvider<GoalRepositoryImpl>(singletonCImpl, 3));
      this.habitTrackRepositoryImplProvider = DoubleCheck.provider(new SwitchingProvider<HabitTrackRepositoryImpl>(singletonCImpl, 4));
      this.changeLogRepositoryImplProvider = DoubleCheck.provider(new SwitchingProvider<ChangeLogRepositoryImpl>(singletonCImpl, 5));
      this.goalChecklistItemRepositoryImplProvider = DoubleCheck.provider(new SwitchingProvider<GoalChecklistItemRepositoryImpl>(singletonCImpl, 6));
      this.userPreferencesRepositoryImplProvider = DoubleCheck.provider(new SwitchingProvider<UserPreferencesRepositoryImpl>(singletonCImpl, 7));
      this.reminderRepositoryImplProvider = DoubleCheck.provider(new SwitchingProvider<ReminderRepositoryImpl>(singletonCImpl, 8));
      this.interceptionPermissionHelperProvider = DoubleCheck.provider(new SwitchingProvider<InterceptionPermissionHelper>(singletonCImpl, 9));
      this.deviceCapabilityCheckerProvider = DoubleCheck.provider(new SwitchingProvider<DeviceCapabilityChecker>(singletonCImpl, 10));
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

      @SuppressWarnings("unchecked")
      @Override
      public T get() {
        switch (id) {
          case 0: // com.dopashift.data.repository.EfficiencyScoreRepositoryImpl 
          return (T) new EfficiencyScoreRepositoryImpl(singletonCImpl.efficiencyScoreDao());

          case 1: // com.dopashift.data.local.DopaShiftDatabase 
          return (T) DataModule_ProvideDatabaseFactory.provideDatabase(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 2: // com.dopashift.data.repository.DailyTodoRepositoryImpl 
          return (T) new DailyTodoRepositoryImpl(singletonCImpl.dailyTodoDao());

          case 3: // com.dopashift.data.repository.GoalRepositoryImpl 
          return (T) new GoalRepositoryImpl(singletonCImpl.goalDao());

          case 4: // com.dopashift.data.repository.HabitTrackRepositoryImpl 
          return (T) new HabitTrackRepositoryImpl(singletonCImpl.habitTrackDao());

          case 5: // com.dopashift.data.repository.ChangeLogRepositoryImpl 
          return (T) new ChangeLogRepositoryImpl(singletonCImpl.changeLogDao());

          case 6: // com.dopashift.data.repository.GoalChecklistItemRepositoryImpl 
          return (T) new GoalChecklistItemRepositoryImpl(singletonCImpl.goalChecklistDao());

          case 7: // com.dopashift.data.repository.UserPreferencesRepositoryImpl 
          return (T) new UserPreferencesRepositoryImpl(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 8: // com.dopashift.data.repository.ReminderRepositoryImpl 
          return (T) new ReminderRepositoryImpl(singletonCImpl.reminderDao());

          case 9: // com.dopashift.interception.InterceptionPermissionHelper 
          return (T) new InterceptionPermissionHelper();

          case 10: // com.dopashift.interception.DeviceCapabilityChecker 
          return (T) new DeviceCapabilityChecker();

          default: throw new AssertionError(id);
        }
      }
    }
  }
}
