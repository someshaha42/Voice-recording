import {
  AecRouteStatus,
  AudioConfig,
  AudioDeviceOption,
  AudioHardwareState,
  BluetoothRouteType,
  createDefaultHardwareState,
} from '../types/audio';

const USB_KEYWORDS = [
  'usb',
  'type-c',
  'type c',
  'rode',
  'shure',
  'blue',
  'yeti',
  'samson',
  'focusrite',
  'scarlett',
  'audio-technica',
  'behringer',
  'elgato',
  'fifine',
  'hyperx',
  'external',
  'interface',
  'dac',
];

const WIRED_HEADPHONE_KEYWORDS = [
  'headphone',
  'headset',
  'earphone',
  'wired',
  'aux',
  'line out',
  '3.5mm',
  'dongle',
];

const BLUETOOTH_KEYWORDS = [
  'bluetooth',
  'airpods',
  'buds',
  'bose',
  'sony',
  'jabra',
  'beats',
  'wireless',
  'hands-free',
  'hfp',
  'hsp',
  'a2dp',
  'headset',
];

export type HardwareListener = (state: AudioHardwareState) => void;

export class DeviceHardwareManager {
  private state: AudioHardwareState = {
    ...createDefaultHardwareState(),
    supportsOutputSinkRouting:
      typeof AudioContext !== 'undefined' && 'setSinkId' in AudioContext.prototype,
  };
  private listeners = new Set<HardwareListener>();
  private deviceChangeHandler: (() => void) | null = null;
  private isMonitoring = false;

  getState(): AudioHardwareState {
    return this.state;
  }

  subscribe(listener: HardwareListener): () => void {
    this.listeners.add(listener);
    listener(this.state);
    return () => {
      this.listeners.delete(listener);
    };
  }

  private emit(): void {
    for (const listener of this.listeners) {
      listener(this.state);
    }
  }

  startMonitoring(): void {
    if (this.isMonitoring) return;
    this.isMonitoring = true;

    if (typeof navigator !== 'undefined' && navigator.mediaDevices?.addEventListener) {
      this.deviceChangeHandler = () => {
        void this.refreshDevices();
      };
      navigator.mediaDevices.addEventListener('devicechange', this.deviceChangeHandler);
    }
    void this.refreshDevices();
  }

  stopMonitoring(): void {
    this.isMonitoring = false;
    if (
      this.deviceChangeHandler &&
      typeof navigator !== 'undefined' &&
      navigator.mediaDevices?.removeEventListener
    ) {
      navigator.mediaDevices.removeEventListener('devicechange', this.deviceChangeHandler);
    }
    this.deviceChangeHandler = null;
  }

  updateActiveCaptureStats(
    sampleRateHz: number,
    channelCount: number,
    deviceId?: string | null,
    deviceLabel?: string | null
  ): void {
    const cleanRate =
      sampleRateHz > 0 ? Math.round(sampleRateHz) : AudioConfig.OUTPUT_SAMPLE_RATE_HZ;
    const label = deviceLabel?.trim() || this.state.inputDeviceName;
    const isUsb = this.matchesKeywords(label, USB_KEYWORDS) || this.state.isUsbMicConnected;
    const isBtIn =
      this.matchesKeywords(label, BLUETOOTH_KEYWORDS) || this.state.isBluetoothInputConnected;

    this.state = {
      ...this.state,
      inputDeviceId: deviceId ?? this.state.inputDeviceId,
      inputDeviceName: label,
      isUsbMicConnected: isUsb,
      isBluetoothInputConnected: isBtIn,
      bluetoothRouteType: this.computeBluetoothRouteType(
        isBtIn,
        this.state.isBluetoothOutputConnected
      ),
      activeInputSampleRateHz: cleanRate,
      activeInputChannelCount: Math.max(1, channelCount || 1),
      isResamplingActive: cleanRate !== AudioConfig.OUTPUT_SAMPLE_RATE_HZ,
      hardwareValidated: true,
    };
    this.emit();
  }

  updateAecDiagnostics(
    aecRequested: boolean,
    pipelineRunning: boolean,
    hardwareSupported: boolean,
    hardwareActive: boolean
  ): void {
    let aecRouteStatus: AecRouteStatus;
    let aecStatusDetail: string;

    if (!aecRequested) {
      aecRouteStatus = 'OFF';
      aecStatusDetail = 'AEC & Noise Gate OFF';
    } else if (!pipelineRunning) {
      aecRouteStatus = 'STANDBY';
      aecStatusDetail =
        this.state.bluetoothRouteType === 'A2DP'
          ? 'Standby • Bluetooth A2DP output uses built-in/USB mic + DSP Noise Gate'
          : this.state.bluetoothRouteType === 'HFP_HSP'
            ? 'Standby • Bluetooth HFP/HSP route ready for AEC & Noise Gate'
            : 'Standby • AEC & Noise Gate armed';
    } else if (hardwareActive) {
      aecRouteStatus = 'ACTIVE_HW_AND_DSP';
      aecStatusDetail =
        this.state.bluetoothRouteType === 'HFP_HSP'
          ? 'Active: Browser HW AEC + 120 Hz HPF + PLC + Noise Gate (Bluetooth HFP)'
          : 'Active: Browser HW AEC + 120 Hz HPF + PLC + Noise Gate';
    } else {
      aecRouteStatus = 'ACTIVE_DSP_ONLY';
      if (this.state.isBluetoothOutputConnected) {
        aecStatusDetail =
          'DSP Gate & 120 Hz HPF active (Hardware AEC unavailable on current Bluetooth route)';
      } else if (this.state.isWiredHeadphoneConnected) {
        aecStatusDetail = 'Active: 120 Hz HPF + PLC + Noise Gate (Headphone direct path)';
      } else {
        aecStatusDetail =
          'DSP Gate & 120 Hz HPF active (Hardware AEC not reported by device driver)';
      }
    }

    this.state = {
      ...this.state,
      aecHardwareSupported: hardwareSupported,
      aecHardwareActive: hardwareActive,
      aecRouteStatus,
      aecStatusDetail,
    };
    this.emit();
  }

  selectPreferredInput(deviceId: string | null): void {
    const match = this.state.availableInputs.find((d) => d.deviceId === deviceId);
    if (!match) return;
    const isBtIn = Boolean(match.isBluetooth);
    this.state = {
      ...this.state,
      inputDeviceId: match.deviceId,
      inputDeviceName: match.label,
      isUsbMicConnected: match.isUsb,
      isBluetoothInputConnected: isBtIn,
      bluetoothRouteType: this.computeBluetoothRouteType(
        isBtIn,
        this.state.isBluetoothOutputConnected
      ),
    };
    this.emit();
  }

  selectPreferredOutput(deviceId: string | null): void {
    const match = this.state.availableOutputs.find((d) => d.deviceId === deviceId);
    if (!match) return;
    const isBtOut = Boolean(match.isBluetooth);
    const isWired =
      !isBtOut && (match.isUsb || this.matchesKeywords(match.label, WIRED_HEADPHONE_KEYWORDS));
    this.state = {
      ...this.state,
      outputDeviceId: match.deviceId,
      outputDeviceName: match.label,
      isWiredHeadphoneConnected: isWired,
      isBluetoothOutputConnected: isBtOut,
      bluetoothRouteType: this.computeBluetoothRouteType(
        this.state.isBluetoothInputConnected,
        isBtOut
      ),
    };
    this.emit();
  }

  async refreshDevices(): Promise<void> {
    if (typeof navigator === 'undefined' || !navigator.mediaDevices?.enumerateDevices) {
      return;
    }

    try {
      const devices = await navigator.mediaDevices.enumerateDevices();
      const inputs: AudioDeviceOption[] = [];
      const outputs: AudioDeviceOption[] = [];

      let inputIdx = 1;
      let outputIdx = 1;

      for (const dev of devices) {
        if (dev.kind === 'audioinput') {
          const rawLabel = dev.label?.trim() || `Microphone ${inputIdx++}`;
          const isBt = this.matchesKeywords(rawLabel, BLUETOOTH_KEYWORDS);
          const isUsb = !isBt && this.matchesKeywords(rawLabel, USB_KEYWORDS);
          inputs.push({
            deviceId: dev.deviceId,
            label: rawLabel,
            isUsb,
            isBluetooth: isBt,
          });
        } else if (dev.kind === 'audiooutput') {
          const rawLabel = dev.label?.trim() || `Audio Output ${outputIdx++}`;
          const isBt = this.matchesKeywords(rawLabel, BLUETOOTH_KEYWORDS);
          const isUsb = !isBt && this.matchesKeywords(rawLabel, USB_KEYWORDS);
          outputs.push({
            deviceId: dev.deviceId,
            label: rawLabel,
            isUsb,
            isBluetooth: isBt,
          });
        }
      }

      // Prioritize explicit current selection if still present, else USB mic, else Bluetooth mic if headset active, else default
      const currentInput = inputs.find((d) => d.deviceId === this.state.inputDeviceId);
      const usbInput = inputs.find((d) => d.isUsb);
      const chosenInput = currentInput ?? usbInput ?? inputs[0] ?? null;

      const currentOutput = outputs.find((d) => d.deviceId === this.state.outputDeviceId);
      const wiredOut = outputs.find(
        (d) =>
          !d.isBluetooth &&
          (d.isUsb || this.matchesKeywords(d.label, WIRED_HEADPHONE_KEYWORDS))
      );
      const btOut = outputs.find((d) => d.isBluetooth);
      const chosenOut = currentOutput ?? wiredOut ?? btOut ?? outputs[0] ?? null;

      const isWired =
        chosenOut != null &&
        !chosenOut.isBluetooth &&
        (chosenOut.isUsb || this.matchesKeywords(chosenOut.label, WIRED_HEADPHONE_KEYWORDS));
      const isBtOut = Boolean(chosenOut?.isBluetooth);
      const isBtIn = Boolean(chosenInput?.isBluetooth);

      this.state = {
        ...this.state,
        inputDeviceId: chosenInput?.deviceId ?? null,
        inputDeviceName: chosenInput?.label ?? 'Internal Microphone',
        isUsbMicConnected: chosenInput?.isUsb ?? false,
        isBluetoothInputConnected: isBtIn,
        outputDeviceId: chosenOut?.deviceId ?? null,
        outputDeviceName: chosenOut?.label ?? 'System Output (Use headphones for preview)',
        isWiredHeadphoneConnected: isWired,
        isBluetoothOutputConnected: isBtOut,
        bluetoothRouteType: this.computeBluetoothRouteType(isBtIn, isBtOut),
        supportsOutputSinkRouting:
          typeof AudioContext !== 'undefined' && 'setSinkId' in AudioContext.prototype,
        hardwareValidated:
          inputs.length > 0 &&
          Boolean(chosenInput?.label && !chosenInput.label.startsWith('Microphone ')),
        availableInputs: inputs,
        availableOutputs: outputs,
      };
      this.emit();
    } catch {
      // Ignore enumerate failures before permissions
    }
  }

  private computeBluetoothRouteType(isBtIn: boolean, isBtOut: boolean): BluetoothRouteType {
    if (isBtIn && isBtOut) return 'HFP_HSP';
    if (isBtOut) return 'A2DP';
    if (isBtIn) return 'HFP_HSP';
    return 'NONE';
  }

  private matchesKeywords(text: string, keywords: string[]): boolean {
    const lower = text.toLowerCase();
    return keywords.some((kw) => lower.includes(kw));
  }
}
