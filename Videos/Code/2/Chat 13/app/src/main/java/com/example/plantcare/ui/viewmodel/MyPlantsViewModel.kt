package com.example.plantcare.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.plantcare.Plant
import com.example.plantcare.RoomCategory
import com.example.plantcare.data.repository.PlantRepository
import com.example.plantcare.data.repository.RoomCategoryRepository
import kotlinx.coroutines.launch

/**
 * ViewModel for MyPlantsFragment — uses PlantRepository + RoomCategoryRepository.
 */
class MyPlantsViewModel(application: Application) : AndroidViewModel(application) {

    private val plantRepo = PlantRepository.getInstance(application)
    private val roomRepo = RoomCategoryRepository.getInstance(application)

    private val _rooms = MutableLiveData<List<RoomCategory>>()
    val rooms: LiveData<List<RoomCategory>> = _rooms

    private val _plantsGroupedByRoom = MutableLiveData<Map<Int, List<Plant>>>()
    val plantsGroupedByRoom: LiveData<Map<Int, List<Plant>>> = _plantsGroupedByRoom

    fun loadRooms(email: String) {
        viewModelScope.launch {
            val roomList = roomRepo.getRoomsListForUser(email)
            _rooms.value = roomList

            val grouping = mutableMapOf<Int, List<Plant>>()
            for (room in roomList) {
                grouping[room.id] = plantRepo.getUserPlantsInRoomList(room.id, email)
            }
            _plantsGroupedByRoom.value = grouping
        }
    }

    fun loadPlantsForRoom(roomId: Int, email: String) {
        viewModelScope.launch {
            val plants = plantRepo.getUserPlantsInRoomList(roomId, email)
            val current = _plantsGroupedByRoom.value?.toMutableMap() ?: mutableMapOf()
            current[roomId] = plants
            _plantsGroupedByRoom.value = current
        }
    }

    fun addRoom(room: RoomCategory) {
        viewModelScope.launch {
            roomRepo.insertRoom(room)
            val current = _rooms.value.orEmpty()
            _rooms.value = current + room
        }
    }

    fun deleteRoom(room: RoomCategory) {
        viewModelScope.launch {
            roomRepo.deleteRoom(room)
            _rooms.value = _rooms.value?.filter { it.id != room.id }.orEmpty()
            val grouping = _plantsGroupedByRoom.value?.toMutableMap() ?: mutableMapOf()
            grouping.remove(room.id)
            _plantsGroupedByRoom.value = grouping
        }
    }
}
