Pod::Spec.new do |s|
    s.name         = "AIMediaKit"
    s.version      = "1.0.0"
    s.summary      = "Native module for high performance image and video processing"
    s.homepage     = "https://ai-illustwitter.art/"
    s.license      = "MIT"
    s.author       = { "Phuong Nguyen" => "nguyenducphuong@ai-illustwitter.art" }
    s.platform     = :ios, "13.0"
    s.source       = { :git => "" }
    s.source_files = "ios/**/*.{h,m,mm,swift,cpp}"
    s.dependency   "React"
    s.dependency   "react-native-nitro-modules"
  
    load 'nitrogen/generated/ios/AIMediaKit+autolinking.rb'
    add_nitrogen_files(s)
  end