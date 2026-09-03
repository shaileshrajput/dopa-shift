package com.dopashift.app;

import androidx.hilt.work.HiltWrapper_WorkerFactoryModule;
import com.dopashift.app.di.QuickCreateRepositoryModule;
import com.dopashift.app.di.QuickCreateUseCaseModule;
import com.dopashift.data.di.DataModule;
import com.dopashift.data.di.NetworkModule;
import com.dopashift.data.di.RepositoryModule;
import com.dopashift.interception.InterceptionService_GeneratedInjector;
import com.dopashift.interception.di.InterceptionModule;
import com.dopashift.interception.overlay.OverlayService_GeneratedInjector;
import com.dopashift.interception.overlay.OverlayViewModel_HiltModules;
import com.dopashift.interception.reminder.ReminderEvaluationWorker_HiltModule;
import com.dopashift.interception.reminder.di.ReminderModule;
import com.dopashift.sync.SyncWorker_HiltModule;
import com.dopashift.sync.di.SyncBindingModule;
import com.dopashift.sync.di.SyncModule;
import com.dopashift.ui.analytics.AnalyticsViewModel_HiltModules;
import com.dopashift.ui.dashboard.DashboardViewModel_HiltModules;
import com.dopashift.ui.goals.GoalsViewModel_HiltModules;
import com.dopashift.ui.habits.HabitRoadmapViewModel_HiltModules;
import com.dopashift.ui.interception.RuleAuthoringViewModel_HiltModules;
import com.dopashift.ui.navigation.DopaShiftAppViewModel_HiltModules;
import com.dopashift.ui.onboarding.OnboardingViewModel_HiltModules;
import com.dopashift.ui.onboarding.PermissionSetupViewModel_HiltModules;
import com.dopashift.ui.quickcreate.QuickCreateViewModel_HiltModules;
import com.dopashift.ui.reminders.RemindersViewModel_HiltModules;
import com.dopashift.ui.settings.AccentPickerViewModel_HiltModules;
import com.dopashift.ui.settings.SettingsViewModel_HiltModules;
import com.dopashift.ui.tasks.DailyTasksViewModel_HiltModules;
import com.dopashift.ui.theme.ThemeStateViewModel_HiltModules;
import dagger.Binds;
import dagger.Component;
import dagger.Module;
import dagger.Subcomponent;
import dagger.hilt.android.components.ActivityComponent;
import dagger.hilt.android.components.ActivityRetainedComponent;
import dagger.hilt.android.components.FragmentComponent;
import dagger.hilt.android.components.ServiceComponent;
import dagger.hilt.android.components.ViewComponent;
import dagger.hilt.android.components.ViewModelComponent;
import dagger.hilt.android.components.ViewWithFragmentComponent;
import dagger.hilt.android.flags.FragmentGetContextFix;
import dagger.hilt.android.flags.HiltWrapper_FragmentGetContextFix_FragmentGetContextFixModule;
import dagger.hilt.android.internal.builders.ActivityComponentBuilder;
import dagger.hilt.android.internal.builders.ActivityRetainedComponentBuilder;
import dagger.hilt.android.internal.builders.FragmentComponentBuilder;
import dagger.hilt.android.internal.builders.ServiceComponentBuilder;
import dagger.hilt.android.internal.builders.ViewComponentBuilder;
import dagger.hilt.android.internal.builders.ViewModelComponentBuilder;
import dagger.hilt.android.internal.builders.ViewWithFragmentComponentBuilder;
import dagger.hilt.android.internal.lifecycle.DefaultViewModelFactories;
import dagger.hilt.android.internal.lifecycle.HiltViewModelFactory;
import dagger.hilt.android.internal.lifecycle.HiltWrapper_DefaultViewModelFactories_ActivityModule;
import dagger.hilt.android.internal.lifecycle.HiltWrapper_HiltViewModelFactory_ActivityCreatorEntryPoint;
import dagger.hilt.android.internal.lifecycle.HiltWrapper_HiltViewModelFactory_ViewModelModule;
import dagger.hilt.android.internal.managers.ActivityComponentManager;
import dagger.hilt.android.internal.managers.FragmentComponentManager;
import dagger.hilt.android.internal.managers.HiltWrapper_ActivityRetainedComponentManager_ActivityRetainedComponentBuilderEntryPoint;
import dagger.hilt.android.internal.managers.HiltWrapper_ActivityRetainedComponentManager_ActivityRetainedLifecycleEntryPoint;
import dagger.hilt.android.internal.managers.HiltWrapper_ActivityRetainedComponentManager_LifecycleModule;
import dagger.hilt.android.internal.managers.HiltWrapper_ActivitySavedStateHandleModule;
import dagger.hilt.android.internal.managers.ServiceComponentManager;
import dagger.hilt.android.internal.managers.ViewComponentManager;
import dagger.hilt.android.internal.modules.ApplicationContextModule;
import dagger.hilt.android.internal.modules.HiltWrapper_ActivityModule;
import dagger.hilt.android.scopes.ActivityRetainedScoped;
import dagger.hilt.android.scopes.ActivityScoped;
import dagger.hilt.android.scopes.FragmentScoped;
import dagger.hilt.android.scopes.ServiceScoped;
import dagger.hilt.android.scopes.ViewModelScoped;
import dagger.hilt.android.scopes.ViewScoped;
import dagger.hilt.components.SingletonComponent;
import dagger.hilt.internal.GeneratedComponent;
import dagger.hilt.migration.DisableInstallInCheck;
import javax.annotation.processing.Generated;
import javax.inject.Singleton;

@Generated("dagger.hilt.processor.internal.root.RootProcessor")
public final class DopaShiftApplication_HiltComponents {
  private DopaShiftApplication_HiltComponents() {
  }

  @Module(
      subcomponents = ServiceC.class
  )
  @DisableInstallInCheck
  @Generated("dagger.hilt.processor.internal.root.RootProcessor")
  abstract interface ServiceCBuilderModule {
    @Binds
    ServiceComponentBuilder bind(ServiceC.Builder builder);
  }

  @Module(
      subcomponents = ActivityRetainedC.class
  )
  @DisableInstallInCheck
  @Generated("dagger.hilt.processor.internal.root.RootProcessor")
  abstract interface ActivityRetainedCBuilderModule {
    @Binds
    ActivityRetainedComponentBuilder bind(ActivityRetainedC.Builder builder);
  }

  @Module(
      subcomponents = ActivityC.class
  )
  @DisableInstallInCheck
  @Generated("dagger.hilt.processor.internal.root.RootProcessor")
  abstract interface ActivityCBuilderModule {
    @Binds
    ActivityComponentBuilder bind(ActivityC.Builder builder);
  }

  @Module(
      subcomponents = ViewModelC.class
  )
  @DisableInstallInCheck
  @Generated("dagger.hilt.processor.internal.root.RootProcessor")
  abstract interface ViewModelCBuilderModule {
    @Binds
    ViewModelComponentBuilder bind(ViewModelC.Builder builder);
  }

  @Module(
      subcomponents = ViewC.class
  )
  @DisableInstallInCheck
  @Generated("dagger.hilt.processor.internal.root.RootProcessor")
  abstract interface ViewCBuilderModule {
    @Binds
    ViewComponentBuilder bind(ViewC.Builder builder);
  }

  @Module(
      subcomponents = FragmentC.class
  )
  @DisableInstallInCheck
  @Generated("dagger.hilt.processor.internal.root.RootProcessor")
  abstract interface FragmentCBuilderModule {
    @Binds
    FragmentComponentBuilder bind(FragmentC.Builder builder);
  }

  @Module(
      subcomponents = ViewWithFragmentC.class
  )
  @DisableInstallInCheck
  @Generated("dagger.hilt.processor.internal.root.RootProcessor")
  abstract interface ViewWithFragmentCBuilderModule {
    @Binds
    ViewWithFragmentComponentBuilder bind(ViewWithFragmentC.Builder builder);
  }

  @Component(
      modules = {
          ApplicationContextModule.class,
          DataModule.class,
          ActivityRetainedCBuilderModule.class,
          ServiceCBuilderModule.class,
          HiltWrapper_FragmentGetContextFix_FragmentGetContextFixModule.class,
          HiltWrapper_WorkerFactoryModule.class,
          InterceptionModule.class,
          NetworkModule.class,
          QuickCreateRepositoryModule.class,
          QuickCreateUseCaseModule.class,
          ReminderEvaluationWorker_HiltModule.class,
          ReminderModule.class,
          RepositoryModule.class,
          SyncBindingModule.class,
          SyncModule.class,
          SyncWorker_HiltModule.class
      }
  )
  @Singleton
  @jakarta.inject.Singleton
  public abstract static class SingletonC implements DopaShiftApplication_GeneratedInjector,
      FragmentGetContextFix.FragmentGetContextFixEntryPoint,
      HiltWrapper_ActivityRetainedComponentManager_ActivityRetainedComponentBuilderEntryPoint,
      ServiceComponentManager.ServiceComponentBuilderEntryPoint,
      SingletonComponent,
      GeneratedComponent {
  }

  @Subcomponent
  @ServiceScoped
  public abstract static class ServiceC implements InterceptionService_GeneratedInjector,
      OverlayService_GeneratedInjector,
      ServiceComponent,
      GeneratedComponent {
    @Subcomponent.Builder
    abstract interface Builder extends ServiceComponentBuilder {
    }
  }

  @Subcomponent(
      modules = {
          AccentPickerViewModel_HiltModules.KeyModule.class,
          AnalyticsViewModel_HiltModules.KeyModule.class,
          DailyTasksViewModel_HiltModules.KeyModule.class,
          DashboardViewModel_HiltModules.KeyModule.class,
          DopaShiftAppViewModel_HiltModules.KeyModule.class,
          ActivityCBuilderModule.class,
          ViewModelCBuilderModule.class,
          GoalsViewModel_HiltModules.KeyModule.class,
          HabitRoadmapViewModel_HiltModules.KeyModule.class,
          HiltWrapper_ActivityRetainedComponentManager_LifecycleModule.class,
          HiltWrapper_ActivitySavedStateHandleModule.class,
          OnboardingViewModel_HiltModules.KeyModule.class,
          OverlayViewModel_HiltModules.KeyModule.class,
          PermissionSetupViewModel_HiltModules.KeyModule.class,
          QuickCreateViewModel_HiltModules.KeyModule.class,
          RemindersViewModel_HiltModules.KeyModule.class,
          RuleAuthoringViewModel_HiltModules.KeyModule.class,
          SettingsViewModel_HiltModules.KeyModule.class,
          ThemeStateViewModel_HiltModules.KeyModule.class
      }
  )
  @ActivityRetainedScoped
  public abstract static class ActivityRetainedC implements ActivityRetainedComponent,
      ActivityComponentManager.ActivityComponentBuilderEntryPoint,
      HiltWrapper_ActivityRetainedComponentManager_ActivityRetainedLifecycleEntryPoint,
      GeneratedComponent {
    @Subcomponent.Builder
    abstract interface Builder extends ActivityRetainedComponentBuilder {
    }
  }

  @Subcomponent(
      modules = {
          FragmentCBuilderModule.class,
          ViewCBuilderModule.class,
          HiltWrapper_ActivityModule.class,
          HiltWrapper_DefaultViewModelFactories_ActivityModule.class
      }
  )
  @ActivityScoped
  public abstract static class ActivityC implements MainActivity_GeneratedInjector,
      ActivityComponent,
      DefaultViewModelFactories.ActivityEntryPoint,
      HiltWrapper_HiltViewModelFactory_ActivityCreatorEntryPoint,
      FragmentComponentManager.FragmentComponentBuilderEntryPoint,
      ViewComponentManager.ViewComponentBuilderEntryPoint,
      GeneratedComponent {
    @Subcomponent.Builder
    abstract interface Builder extends ActivityComponentBuilder {
    }
  }

  @Subcomponent(
      modules = {
          AccentPickerViewModel_HiltModules.BindsModule.class,
          AnalyticsViewModel_HiltModules.BindsModule.class,
          DailyTasksViewModel_HiltModules.BindsModule.class,
          DashboardViewModel_HiltModules.BindsModule.class,
          DopaShiftAppViewModel_HiltModules.BindsModule.class,
          GoalsViewModel_HiltModules.BindsModule.class,
          HabitRoadmapViewModel_HiltModules.BindsModule.class,
          HiltWrapper_HiltViewModelFactory_ViewModelModule.class,
          OnboardingViewModel_HiltModules.BindsModule.class,
          OverlayViewModel_HiltModules.BindsModule.class,
          PermissionSetupViewModel_HiltModules.BindsModule.class,
          QuickCreateViewModel_HiltModules.BindsModule.class,
          RemindersViewModel_HiltModules.BindsModule.class,
          RuleAuthoringViewModel_HiltModules.BindsModule.class,
          SettingsViewModel_HiltModules.BindsModule.class,
          ThemeStateViewModel_HiltModules.BindsModule.class
      }
  )
  @ViewModelScoped
  public abstract static class ViewModelC implements ViewModelComponent,
      HiltViewModelFactory.ViewModelFactoriesEntryPoint,
      GeneratedComponent {
    @Subcomponent.Builder
    abstract interface Builder extends ViewModelComponentBuilder {
    }
  }

  @Subcomponent
  @ViewScoped
  public abstract static class ViewC implements ViewComponent,
      GeneratedComponent {
    @Subcomponent.Builder
    abstract interface Builder extends ViewComponentBuilder {
    }
  }

  @Subcomponent(
      modules = ViewWithFragmentCBuilderModule.class
  )
  @FragmentScoped
  public abstract static class FragmentC implements FragmentComponent,
      DefaultViewModelFactories.FragmentEntryPoint,
      ViewComponentManager.ViewWithFragmentComponentBuilderEntryPoint,
      GeneratedComponent {
    @Subcomponent.Builder
    abstract interface Builder extends FragmentComponentBuilder {
    }
  }

  @Subcomponent
  @ViewScoped
  public abstract static class ViewWithFragmentC implements ViewWithFragmentComponent,
      GeneratedComponent {
    @Subcomponent.Builder
    abstract interface Builder extends ViewWithFragmentComponentBuilder {
    }
  }
}
