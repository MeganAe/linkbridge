; LinkBridge — installateur Windows (Inno Setup)
;
; Compile avec :
;   iscc /DAppVersion=0.3.0 installer\linkbridge.iss
;
; L'app-image (jpackage) est produit par :
;   gradle :desktop:createDistributable -PappVersion=0.3.0
; et se trouve par défaut dans desktop\build\compose\binaries\main\app\LinkBridge.

#ifndef AppVersion
  #define AppVersion "0.3.0"
#endif
#ifndef AppImageDir
  #define AppImageDir "..\desktop\build\compose\binaries\main\app\LinkBridge"
#endif

; ---------------------------------------------------------------------------
; VersionInfoVersion doit être composée de quatre nombres, alors que AppVersion
; en compte trois. On complète avec des zéros : 0.3.0 devient 0.3.0.0.
; ---------------------------------------------------------------------------
#define Dot1 Pos(".", AppVersion)
#if Dot1 == 0
  #define VersionInfoVersion AppVersion + ".0.0.0"
#else
  #define VerMajor Copy(AppVersion, 1, Dot1 - 1)
  #define AfterMajor Copy(AppVersion, Dot1 + 1, 64)
  #define Dot2 Pos(".", AfterMajor)
  #if Dot2 == 0
    #define VersionInfoVersion VerMajor + "." + AfterMajor + ".0.0"
  #else
    #define VerMinor Copy(AfterMajor, 1, Dot2 - 1)
    #define AfterMinor Copy(AfterMajor, Dot2 + 1, 64)
    #define Dot3 Pos(".", AfterMinor)
    #if Dot3 == 0
      #define VersionInfoVersion VerMajor + "." + VerMinor + "." + AfterMinor + ".0"
    #else
      #define VerBuild Copy(AfterMinor, 1, Dot3 - 1)
      #define VerRevision Copy(AfterMinor, Dot3 + 1, 64)
      #define VersionInfoVersion VerMajor + "." + VerMinor + "." + VerBuild + "." + VerRevision
    #endif
  #endif
#endif

#define AppAuthor "Metoushela Walker"
#define AppRepository "https://github.com/MeganAe/linkbridge"

[Setup]
AppId={{3F2A5E91-8C4D-4B7A-A1E2-9D5C7B3F6A20}
AppName=LinkBridge
AppVersion={#AppVersion}
AppVerName=LinkBridge {#AppVersion}
AppPublisher={#AppAuthor}
AppPublisherURL={#AppRepository}
AppSupportURL={#AppRepository}
AppUpdatesURL={#AppRepository}
AppCopyright=© 2026 {#AppAuthor}. Logiciel sous licence MIT.
; Métadonnées du fichier .exe, visibles dans les propriétés de l'installateur.
VersionInfoCompany={#AppAuthor}
VersionInfoProductName=LinkBridge
VersionInfoDescription=LinkBridge : un pont, pas un hotspot.
VersionInfoCopyright=© 2026 {#AppAuthor}
VersionInfoVersion={#VersionInfoVersion}
; Le texte de licence est présenté par l'installateur avant l'installation.
LicenseFile=..\LICENSE
; Installation par utilisateur, sans droits administrateur.
DefaultDirName={localappdata}\Programs\LinkBridge
PrivilegesRequired=lowest
DisableProgramGroupPage=yes
ArchitecturesAllowed=x64
ArchitecturesInstallIn64BitMode=x64
OutputDir=..\dist
OutputBaseFilename=LinkBridge-Setup-{#AppVersion}
SetupIconFile=linkbridge.ico
UninstallDisplayIcon={app}\LinkBridge.exe
UninstallDisplayName=LinkBridge
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
CloseApplications=yes

[Languages]
Name: "fr"; MessagesFile: "compiler:Languages\French.isl"

[Tasks]
Name: "desktopicon"; Description: "Créer un raccourci sur le Bureau"; Flags: unchecked

[Files]
; L'application embarque son runtime Java : l'utilisateur n'a rien à installer.
Source: "{#AppImageDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\LinkBridge"; Filename: "{app}\LinkBridge.exe"
Name: "{autodesktop}\LinkBridge"; Filename: "{app}\LinkBridge.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\LinkBridge.exe"; Description: "Lancer LinkBridge"; Flags: nowait postinstall skipifsilent

[Messages]
fr.FinishedLabel=Setup a terminé l'installation de [name] sur votre ordinateur.%n%nAttention : au premier lancement du relais, Windows peut demander d'autoriser LinkBridge dans le pare-feu, sur le port 39876. Cliquez sur « Autoriser l'accès » pour que le partage fonctionne.
