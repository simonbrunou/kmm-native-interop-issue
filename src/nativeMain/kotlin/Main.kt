import bluetooth.*
import kotlinx.cinterop.*
import platform.posix.GUID
import platform.posix.NULL
import platform.posix.printf
import platform.windows.*

private const val TO_SEARCH_DEVICE_UUID = "{0000180D-0000-1000-8000-00805F9B34FB}"

private fun somethingHappened(
    eventType: BTH_LE_GATT_EVENT_TYPE,
    eventOutParameter: PVOID,
    context: PVOID
) {
    printf("notification obtained ")
    val valueChangedEventParameters: PBLUETOOTH_GATT_VALUE_CHANGED_EVENT =
        eventOutParameter.reinterpret()
    val hr: HRESULT
    if (0u == valueChangedEventParameters.pointed.CharacteristicValue?.pointed?.DataSize) {
        hr = E_FAIL
        printf("datasize 0\n")
    } else {
        printf("HR ")
        val heartRate: Int =
            if (0x01 ==
                    (valueChangedEventParameters.pointed.CharacteristicValue
                        ?.pointed
                        ?.Data?.get(0)
                        ?.toInt()
                        ?: 0) and 0x01
            ) {
                (valueChangedEventParameters.pointed.CharacteristicValue
                    ?.pointed
                    ?.Data?.get(1)
                    ?.toInt()
                    ?: 0) * 256 +
                    (valueChangedEventParameters.pointed.CharacteristicValue
                        ?.pointed
                        ?.Data?.get(2)
                        ?.toInt()
                        ?: 0)
            } else {
                (valueChangedEventParameters.pointed.CharacteristicValue?.pointed?.Data?.get(1)?.toInt()
                    ?: 0)
            }
        printf("%d\n", heartRate)
    }
}

fun getBLEHandle(aGUID: GUID): HANDLE? = memScoped {
    val hDI: HDEVINFO?
    val did: SP_DEVICE_INTERFACE_DATA = alloc()
    val dd: SP_DEVINFO_DATA = alloc()
    val bluetoothInterfaceGUID: GUID = aGUID
    var hComm: HANDLE? = NULL
    hDI =
        SetupDiGetClassDevs?.let {
            it(
                bluetoothInterfaceGUID.ptr,
                NULL?.reinterpret(),
                NULL?.reinterpret(),
                (DIGCF_DEVICEINTERFACE or DIGCF_PRESENT).toUInt()
            )
        }
    if (hDI == INVALID_HANDLE_VALUE) return NULL
    did.cbSize = sizeOf<SP_DEVICE_INTERFACE_DATA>().toUInt()
    dd.cbSize = sizeOf<SP_DEVINFO_DATA>().toUInt()
    var i: DWORD = 0u
    while (SetupDiEnumDeviceInterfaces(
        hDI,
        NULL?.reinterpret(),
        bluetoothInterfaceGUID.ptr,
        i,
        did.ptr
    ) > 0) {
        val deviceInterfaceDetailData: SP_DEVICE_INTERFACE_DETAIL_DATA = alloc()
        deviceInterfaceDetailData.cbSize = sizeOf<SP_DEVICE_INTERFACE_DETAIL_DATA>().toUInt()
        val size: DWORDVar = alloc()
        if ((SetupDiGetDeviceInterfaceDetail?.let {
                it(hDI, did.ptr, NULL?.reinterpret(), 0u, size.ptr, alloc())
            }
                ?: 0) <= 0
        ) {
            val err = GetLastError()
            if (err.toInt() == ERROR_NO_MORE_ITEMS) break
            val pInterfaceDetailData: PSP_DEVICE_INTERFACE_DETAIL_DATA? =
                GlobalAlloc(GPTR, size.value.toULong())?.reinterpret()
            pInterfaceDetailData?.pointed?.cbSize =
                sizeOf<SP_DEVICE_INTERFACE_DETAIL_DATA>().toUInt()
            if ((SetupDiGetDeviceInterfaceDetail?.let {
                    it(hDI, did.ptr, pInterfaceDetailData, size.value, size.ptr, dd.ptr)
                }
                    ?: 0) <= 0
            ) {
                break
            }
            hComm =
                CreateFile?.let {
                    it(
                        pInterfaceDetailData?.pointed?.DevicePath?.reinterpret(),
                        (GENERIC_WRITE or GENERIC_READ.toInt()).toUInt(),
                        (FILE_SHARE_READ or FILE_SHARE_WRITE).toUInt(),
                        NULL?.reinterpret(),
                        OPEN_EXISTING.toUInt(),
                        0u,
                        NULL?.reinterpret()
                    )
                }
            GlobalFree(pInterfaceDetailData)
        }
        i++
    }
    SetupDiDestroyDeviceInfoList(hDI)
    return hComm
}

fun main() {
    printf("Hello, world!")
}
// int main( int argc, char *argv[ ], char *envp[ ] )
// {
//	//Step 1: find the BLE device handle from its GUID
//	GUID AGuid;
//	//GUID can be constructed from "{xxx....}" string using CLSID
//	CLSIDFromString(TEXT(TO_SEARCH_DEVICE_UUID), &AGuid);
//
//	//now get the handle
//	HANDLE hLEDevice = GetBLEHandle(AGuid);
//
//
//	//Step 2: Get a list of services that the device advertises
//	// first send 0,NULL as the parameters to BluetoothGATTServices inorder to get the number of
//	// services in serviceBufferCount
//	USHORT serviceBufferCount;
//	////////////////////////////////////////////////////////////////////////////
//	// Determine Services Buffer Size
//	////////////////////////////////////////////////////////////////////////////
//
//	HRESULT hr = BluetoothGATTGetServices(
//		hLEDevice,
//		0,
//		NULL,
//		&serviceBufferCount,
//		BLUETOOTH_GATT_FLAG_NONE);
//
//	if (HRESULT_FROM_WIN32(ERROR_MORE_DATA) != hr) {
//		printf("BluetoothGATTGetServices - Buffer Size %d", hr);
//	}
//
//	PBTH_LE_GATT_SERVICE pServiceBuffer = (PBTH_LE_GATT_SERVICE)
//		malloc(sizeof(BTH_LE_GATT_SERVICE) * serviceBufferCount);
//
//	if (NULL == pServiceBuffer) {
//		printf("pServiceBuffer out of memory\r\n");
//	} else {
//		RtlZeroMemory(pServiceBuffer,
//			sizeof(BTH_LE_GATT_SERVICE) * serviceBufferCount);
//	}
//
//	////////////////////////////////////////////////////////////////////////////
//	// Retrieve Services
//	////////////////////////////////////////////////////////////////////////////
//
//	USHORT numServices;
//	hr = BluetoothGATTGetServices(
//		hLEDevice,
//		serviceBufferCount,
//		pServiceBuffer,
//		&numServices,
//		BLUETOOTH_GATT_FLAG_NONE);
//
//	if (S_OK != hr) {
//		printf("BluetoothGATTGetServices - Buffer Size %d", hr);
//	}
//
//
//	//Step 3: now get the list of charactersitics. note how the pServiceBuffer is required from step
// 2
//	////////////////////////////////////////////////////////////////////////////
//	// Determine Characteristic Buffer Size
//	////////////////////////////////////////////////////////////////////////////
//
//	USHORT charBufferSize;
//	hr = BluetoothGATTGetCharacteristics(
//		hLEDevice,
//		pServiceBuffer,
//		0,
//		NULL,
//		&charBufferSize,
//		BLUETOOTH_GATT_FLAG_NONE);
//
//	if (HRESULT_FROM_WIN32(ERROR_MORE_DATA) != hr) {
//		printf("BluetoothGATTGetCharacteristics - Buffer Size %d", hr);
//	}
//
//	PBTH_LE_GATT_CHARACTERISTIC pCharBuffer;
//	if (charBufferSize > 0) {
//		pCharBuffer = (PBTH_LE_GATT_CHARACTERISTIC)
//			malloc(charBufferSize * sizeof(BTH_LE_GATT_CHARACTERISTIC));
//
//		if (NULL == pCharBuffer) {
//			printf("pCharBuffer out of memory\r\n");
//		} else {
//			RtlZeroMemory(pCharBuffer,
//				charBufferSize * sizeof(BTH_LE_GATT_CHARACTERISTIC));
//		}
//
//		////////////////////////////////////////////////////////////////////////////
//		// Retrieve Characteristics
//		////////////////////////////////////////////////////////////////////////////
//		USHORT numChars;
//		hr = BluetoothGATTGetCharacteristics(
//			hLEDevice,
//			pServiceBuffer,
//			charBufferSize,
//			pCharBuffer,
//			&numChars,
//			BLUETOOTH_GATT_FLAG_NONE);
//
//		if (S_OK != hr) {
//			printf("BluetoothGATTGetCharacteristics - Actual Data %d", hr);
//		}
//
//		if (numChars != charBufferSize) {
//			printf("buffer size and buffer size actual size mismatch\r\n");
//		}
//	}
//
//
//	//Step 4: now get the list of descriptors. note how the pCharBuffer is required from step 3
//	//descriptors are required as we descriptors that are notification based will have to be written
//	//once IsSubcribeToNotification set to true, we set the appropriate callback function
//	//need for setting descriptors for notification according to
//	//http://social.msdn.microsoft.com/Forums/en-US/11d3a7ce-182b-4190-bf9d-64fefc3328d9/windows-bluetooth-le-apis-event-callbacks?forum=wdk
//	PBTH_LE_GATT_CHARACTERISTIC currGattChar;
//	for (int ii=0; ii <charBufferSize; ii++) {
//		currGattChar = &pCharBuffer[ii];
//		USHORT charValueDataSize;
//		PBTH_LE_GATT_CHARACTERISTIC_VALUE pCharValueBuffer;
//
//
//		///////////////////////////////////////////////////////////////////////////
//		// Determine Descriptor Buffer Size
//		////////////////////////////////////////////////////////////////////////////
//		USHORT descriptorBufferSize;
//		hr = BluetoothGATTGetDescriptors(
//			hLEDevice,
//			currGattChar,
//			0,
//			NULL,
//			&descriptorBufferSize,
//			BLUETOOTH_GATT_FLAG_NONE);
//
//		if (HRESULT_FROM_WIN32(ERROR_MORE_DATA) != hr) {
//			printf("BluetoothGATTGetDescriptors - Buffer Size %d", hr);
//		}
//
//		PBTH_LE_GATT_DESCRIPTOR pDescriptorBuffer;
//		if (descriptorBufferSize > 0) {
//			pDescriptorBuffer = (PBTH_LE_GATT_DESCRIPTOR)
//				malloc(descriptorBufferSize
//				* sizeof(BTH_LE_GATT_DESCRIPTOR));
//
//			if (NULL == pDescriptorBuffer) {
//				printf("pDescriptorBuffer out of memory\r\n");
//			} else {
//				RtlZeroMemory(pDescriptorBuffer, descriptorBufferSize);
//			}
//
//			////////////////////////////////////////////////////////////////////////////
//			// Retrieve Descriptors
//			////////////////////////////////////////////////////////////////////////////
//
//			USHORT numDescriptors;
//			hr = BluetoothGATTGetDescriptors(
//				hLEDevice,
//				currGattChar,
//				descriptorBufferSize,
//				pDescriptorBuffer,
//				&numDescriptors,
//				BLUETOOTH_GATT_FLAG_NONE);
//
//			if (S_OK != hr) {
//				printf("BluetoothGATTGetDescriptors - Actual Data %d", hr);
//			}
//
//			if (numDescriptors != descriptorBufferSize) {
//				printf("buffer size and buffer size actual size mismatch\r\n");
//			}
//
//			for(int kk=0; kk<numDescriptors; kk++) {
//				PBTH_LE_GATT_DESCRIPTOR  currGattDescriptor = &pDescriptorBuffer[kk];
//				////////////////////////////////////////////////////////////////////////////
//				// Determine Descriptor Value Buffer Size
//				////////////////////////////////////////////////////////////////////////////
//				USHORT descValueDataSize;
//				hr = BluetoothGATTGetDescriptorValue(
//					hLEDevice,
//					currGattDescriptor,
//					0,
//					NULL,
//					&descValueDataSize,
//					BLUETOOTH_GATT_FLAG_NONE);
//
//				if (HRESULT_FROM_WIN32(ERROR_MORE_DATA) != hr) {
//					printf("BluetoothGATTGetDescriptorValue - Buffer Size %d", hr);
//				}
//
//				PBTH_LE_GATT_DESCRIPTOR_VALUE pDescValueBuffer =
// (PBTH_LE_GATT_DESCRIPTOR_VALUE)malloc(descValueDataSize);
//
//				if (NULL == pDescValueBuffer) {
//					printf("pDescValueBuffer out of memory\r\n");
//				} else {
//					RtlZeroMemory(pDescValueBuffer, descValueDataSize);
//				}
//
//				////////////////////////////////////////////////////////////////////////////
//				// Retrieve the Descriptor Value
//				////////////////////////////////////////////////////////////////////////////
//
//				hr = BluetoothGATTGetDescriptorValue(
//					hLEDevice,
//					currGattDescriptor,
//					(ULONG)descValueDataSize,
//					pDescValueBuffer,
//					NULL,
//					BLUETOOTH_GATT_FLAG_NONE);
//				if (S_OK != hr) {
//					printf("BluetoothGATTGetDescriptorValue - Actual Data %d", hr);
//				}
//				//you may also get a descriptor that is read (and not notify) andi am guessing the attribute
// handle is out of limits
//				// we set all descriptors that are notifiable to notify us via IsSubstcibeToNotification
//				if(currGattDescriptor->AttributeHandle < 255) {
//					BTH_LE_GATT_DESCRIPTOR_VALUE newValue;
//
//					RtlZeroMemory(&newValue, sizeof(newValue));
//
//					newValue.DescriptorType = ClientCharacteristicConfiguration;
//					newValue.ClientCharacteristicConfiguration.IsSubscribeToNotification = TRUE;
//
//					hr = BluetoothGATTSetDescriptorValue(
//						hLEDevice,
//						currGattDescriptor,
//						&newValue,
//						BLUETOOTH_GATT_FLAG_NONE);
//					if (S_OK != hr) {
//						printf("BluetoothGATTGetDescriptorValue - Actual Data %d", hr);
//					} else {
//						printf("setting notification for serivice handle %d\n", currGattDescriptor->ServiceHandle);
//					}
//
//				}
//
//			}
//		}
//
//
//		//set the appropriate callback function when the descriptor change value
//		BLUETOOTH_GATT_EVENT_HANDLE EventHandle;
//
//		if (currGattChar->IsNotifiable) {
//			printf("Setting Notification for ServiceHandle %d\n",currGattChar->ServiceHandle);
//			BTH_LE_GATT_EVENT_TYPE EventType = CharacteristicValueChangedEvent;
//
//			BLUETOOTH_GATT_VALUE_CHANGED_EVENT_REGISTRATION EventParameterIn;
//			EventParameterIn.Characteristics[0] = *currGattChar;
//			EventParameterIn.NumCharacteristics = 1;
//			hr = BluetoothGATTRegisterEvent(
//				hLEDevice,
//				EventType,
//				&EventParameterIn,
//				SomethingHappened,
//				NULL,
//				&EventHandle,
//				BLUETOOTH_GATT_FLAG_NONE);
//
//			if (S_OK != hr) {
//				printf("BluetoothGATTRegisterEvent - Actual Data %d", hr);
//			}
//		}
//
//
//		if (currGattChar->IsReadable) {//currGattChar->IsReadable
//			////////////////////////////////////////////////////////////////////////////
//			// Determine Characteristic Value Buffer Size
//			////////////////////////////////////////////////////////////////////////////
//			hr = BluetoothGATTGetCharacteristicValue(
//				hLEDevice,
//				currGattChar,
//				0,
//				NULL,
//				&charValueDataSize,
//				BLUETOOTH_GATT_FLAG_NONE);
//
//			if (HRESULT_FROM_WIN32(ERROR_MORE_DATA) != hr) {
//				printf("BluetoothGATTGetCharacteristicValue - Buffer Size %d", hr);
//			}
//
//			pCharValueBuffer = (PBTH_LE_GATT_CHARACTERISTIC_VALUE)malloc(charValueDataSize);
//
//			if (NULL == pCharValueBuffer) {
//				printf("pCharValueBuffer out of memory\r\n");
//			} else {
//				RtlZeroMemory(pCharValueBuffer, charValueDataSize);
//			}
//
//			////////////////////////////////////////////////////////////////////////////
//			// Retrieve the Characteristic Value
//			////////////////////////////////////////////////////////////////////////////
//
//			hr = BluetoothGATTGetCharacteristicValue(
//				hLEDevice,
//				currGattChar,
//				(ULONG)charValueDataSize,
//				pCharValueBuffer,
//				NULL,
//				BLUETOOTH_GATT_FLAG_NONE);
//
//			if (S_OK != hr) {
//				printf("BluetoothGATTGetCharacteristicValue - Actual Data %d", hr);
//			}
//
//			//print the characeteristic Value
//			//for an BLE device this might be the sensor orientation
//			printf("\n Printing a read (not notifiable) characterstic (maybe) body sensor value");
//			for(int iii=0; iii< pCharValueBuffer->DataSize; iii++) {// ideally check ->DataSize before
// printing
//				printf("%d",pCharValueBuffer->Data[iii]);
//			}
//			printf("\n");
//
//
//			// Free before going to next iteration, or memory leak.
//			free(pCharValueBuffer);
//			pCharValueBuffer = NULL;
//		}
//
//	}
//
//	//go into an inf loop that sleeps. you will ideally see notifications from the HR device
//	while(1){
//		Sleep(1000);
//		//printf("look for notification");
//	}
//
//	CloseHandle(hLEDevice);
//
//
//
//	if ( GetLastError()!=NO_ERROR &&
//		GetLastError()!=ERROR_NO_MORE_ITEMS )
//	{
//		// Insert error handling here.
//		return 1;
//	}
//
//	return 0;
// }
