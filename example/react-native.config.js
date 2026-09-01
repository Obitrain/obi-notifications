const path = require('path');
const pkg = require('../package.json');

module.exports = {
  project: {
    ios: {
      automaticPodsInstallation: true,
    },
  },
  dependencies: {
    [pkg.name]: {
      root: path.join(__dirname, '..'),
      platforms: {
        // Codegen script incorrectly fails without this
        // So we explicitly specify the platforms with empty object
        ios: {
          // two podspecs at the package root; pick the Nitro one
          podspecPath: path.join(
            __dirname,
            '..',
            'ReactNativeNotifications.podspec'
          ),
        },
        android: {},
      },
    },
  },
};
