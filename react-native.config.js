// https://github.com/react-native-community/cli/blob/main/docs/dependencies.md
const path = require('path');

module.exports = {
  dependency: {
    platforms: {
      ios: {
        // two podspecs live at the package root; autolink the Nitro one
        // (ObiNotificationsCore is added explicitly in the app Podfile)
        podspecPath: path.join(__dirname, 'ReactNativeNotifications.podspec'),
      },
      android: {},
    },
  },
};
