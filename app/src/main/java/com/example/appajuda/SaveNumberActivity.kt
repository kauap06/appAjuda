package com.example.appajuda

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.appajuda.databinding.ActivitySaveNumberBinding
import com.example.appajuda.databinding.ListItemNumberBinding

class SaveNumberActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySaveNumberBinding
    private lateinit var adapter: NumbersAdapter
    private val numbersList = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySaveNumberBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.editTextNumber.inputType = InputType.TYPE_CLASS_PHONE
        binding.editTextNumber.filters = arrayOf(
            InputFilter { source, start, end, _, _, _ ->
                for (i in start until end) {
                    if (!Character.isDigit(source[i])) return@InputFilter ""
                }
                null
            },
            InputFilter.LengthFilter(11)
        )

        binding.btnVoltar.setOnClickListener {
            val voltar = Intent(this, MainActivity::class.java)
            voltar.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(voltar)
        }

        binding.btnDelete.setOnClickListener {
            deleteActiveNumber()
        }

        adapter = NumbersAdapter(numbersList) { selectedNumber -> onNumberSelected(selectedNumber) }
        binding.recyclerViewNumbers.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewNumbers.adapter = adapter

        loadSavedNumbers()
        loadActiveNumber()

        binding.btnSave.setOnClickListener {
            var phoneNumber = binding.editTextNumber.text.toString().trim()

            if (phoneNumber.length == 10 && phoneNumber.all { it.isDigit() }) {
                val ddd = phoneNumber.substring(0, 2).toInt()
                val validDddsForNine = listOf(
                    11, 12, 13, 14, 15, 16, 17, 18, 19, 21, 22, 24, 27, 28, 31, 32, 33, 34, 35,
                    37, 38, 41, 42, 43, 44, 45, 46, 47, 48, 49, 51, 53, 54, 55, 61, 62, 63, 64,
                    65, 66, 67, 68, 69, 71, 73, 74, 75, 77, 79, 81, 82, 83, 84, 85, 86, 87, 88,
                    89, 91, 92, 93, 94, 95, 96, 97, 98, 99
                )
                if (validDddsForNine.contains(ddd)) {
                    phoneNumber = phoneNumber.substring(0, 2) + "9" + phoneNumber.substring(2)
                }
            }

            if (phoneNumber.length == 11 && phoneNumber.all { it.isDigit() }) {
                val fullNumber = "55$phoneNumber"
                if (numbersList.size < 5 && !numbersList.contains(fullNumber)) {
                    numbersList.add(fullNumber)
                    saveNumbers(numbersList)
                    adapter.notifyDataSetChanged()
                    binding.editTextNumber.text.clear()
                    Toast.makeText(this, "Número salvo com sucesso!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Limite de números atingido ou número já cadastrado.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Digite um número válido com 10 ou 11 dígitos (DDD + número).", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveNumbers(numbers: List<String>) {
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().putStringSet("emergency_numbers", numbers.toSet()).apply()
    }

    private fun loadSavedNumbers() {
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val savedNumbers = sharedPreferences.getStringSet("emergency_numbers", emptySet())?.toList() ?: emptyList()
        numbersList.clear()
        numbersList.addAll(savedNumbers)
        adapter.notifyDataSetChanged()
    }

    private fun loadActiveNumber() {
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val activeNumber = sharedPreferences.getString("active_number", null)
        if (!activeNumber.isNullOrEmpty()) {
            binding.textNum.text = "Número ativo: ${formatPhoneNumber(activeNumber)}"
        } else {
            binding.textNum.text = "Número ativo: Nenhum"
        }
    }

    private fun onNumberSelected(selectedNumber: String) {
        binding.textNum.text = "Número ativo: ${formatPhoneNumber(selectedNumber)}"
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().putString("active_number", selectedNumber).apply()
        // Abertura do WhatsApp REMOVIDA
    }

    private fun deleteActiveNumber() {
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val activeNumber = sharedPreferences.getString("active_number", null)

        if (!activeNumber.isNullOrEmpty()) {
            sharedPreferences.edit().remove("active_number").apply()
            numbersList.remove(activeNumber)
            saveNumbers(numbersList)
            adapter.notifyDataSetChanged()
            binding.textNum.text = "Número ativo: Nenhum"
            Toast.makeText(this, "Número apagado.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Nenhum número selecionado.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatPhoneNumber(number: String): String {
        if (number.length == 13 && number.startsWith("55")) {
            val ddd = number.substring(2, 4)
            var prefix = number.substring(4, 9)
            val suffix = number.substring(9)

            if (prefix.startsWith("9")) {
                prefix = prefix.substring(1)
            }

            return "($ddd) $prefix-$suffix"
        }
        return number
    }

    class NumbersAdapter(
        private val numbers: MutableList<String>,
        private val onClick: (String) -> Unit
    ) : RecyclerView.Adapter<NumbersAdapter.NumberViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NumberViewHolder {
            val binding = ListItemNumberBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return NumberViewHolder(binding)
        }

        override fun onBindViewHolder(holder: NumberViewHolder, position: Int) {
            holder.bind(numbers[position])
        }

        override fun getItemCount(): Int = numbers.size

        inner class NumberViewHolder(private val binding: ListItemNumberBinding) :
            RecyclerView.ViewHolder(binding.root) {

            fun bind(number: String) {
                binding.textNumber.text = formatPhoneNumber(number)
                binding.root.setOnClickListener { onClick(number) }
            }

            private fun formatPhoneNumber(number: String): String {
                if (number.length == 13 && number.startsWith("55")) {
                    val ddd = number.substring(2, 4)
                    var prefix = number.substring(4, 9)
                    val suffix = number.substring(9)

                    if (prefix.startsWith("9")) {
                        prefix = prefix.substring(1)
                    }

                    return "($ddd) $prefix-$suffix"
                }
                return number
            }
        }
    }
}
