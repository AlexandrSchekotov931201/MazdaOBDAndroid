package car.mazda.obd.android.ui.command

sealed class MainViewCommand {
    data object SoundGreeting : MainViewCommand()
    data object SoundGoodbye : MainViewCommand()
    data object SoundWarmupWarning : MainViewCommand()
    data object SoundOverheatWarning : MainViewCommand()
}
