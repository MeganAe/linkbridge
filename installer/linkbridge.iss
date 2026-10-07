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

[Setup]
AppId={{3F2A5E91-8C4D-4B7A-A1E2-9D5C7B3F6A20}
AppName=LinkBridge
AppVersion={#AppVersion}
AppPublisher=LinkBridge
AppPublisherURL=https://github.com/MeganAe/linkbridge
AppSupportURL=https://github.com/MeganAe/linkbridge
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
fr.FinishedLabel=Setup a terminé l'installation de [name] sur votre ordinateur.%n%nAttention : au premier lancement du relais, Windows peut demander d'autoriser LinkBridge dans le pare-feu (port %n39876). Cliquez sur « Autoriser l'accès » pour que le partage fonctionne.
