Pod::Spec.new do |s|
    s.name         = "AIMediaKit"
    s.version      = "1.0.3"
    s.summary      = "Native module for high performance image and video processing"
    s.homepage     = "https://ai-illustwitter.art/"
    s.license      = "MIT"
    s.author       = { "Phuong Nguyen" => "nguyenducphuong@ai-illustwitter.art" }
    s.platform     = :ios, "13.0"
    s.source       = { :git => "" }
    s.source_files = "ios/**/*.{h,m,mm,swift,cpp}"
    s.dependency   "React"
    s.dependency   "react-native-nitro-modules"
  
    s.source_files = [
    # Implementation (Swift)
    "ios/**/*.{swift}",
    # Autolinking/Registration (Objective-C++)
    "ios/**/*.{m,mm}",
    # Implementation (C++ objects)
    "cpp/**/*.{hpp,cpp}",
    ]

    s.pod_target_xcconfig = {
      # C++ compiler flags, mainly for folly.
      "GCC_PREPROCESSOR_DEFINITIONS" => "$(inherited) FOLLY_NO_CONFIG FOLLY_CFG_NO_COROUTINES"
    }

    load 'nitrogen/generated/ios/AIMediaKit+autolinking.rb'
    add_nitrogen_files(s)

    if respond_to?(:install_modules_dependencies, true)
      install_modules_dependencies(s)
    else
      s.dependency "React-Core"
    end
  end