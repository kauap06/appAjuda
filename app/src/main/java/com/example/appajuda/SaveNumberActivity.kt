package com.example.appajuda

import android.content.Intent
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.appajuda.databinding.ActivitySaveNumberBinding
import com.example.appajuda.databinding.ListItemNumberBinding // Importar o binding do item da lista
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions

// Data class para representar um contato de emergência com seu ID do documento
data class EmergencyContact(val id: String = "", val number: String = "")

class SaveNumberActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySaveNumberBinding
    private lateinit var adapter: NumbersAdapter
    private val numbersList = mutableListOf<EmergencyContact>()

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var firestoreListener: ListenerRegistration? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        binding = ActivitySaveNumberBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Se não houver usuário logado, redireciona para o login e finaliza a activity
        if (auth.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setupUI()
        loadUserData()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Remove o listener do Firestore para evitar memory leaks
        firestoreListener?.remove()
    }

    private fun setupUI() {
        binding.editTextNumber.inputType = InputType.TYPE_CLASS_PHONE
        // Filtro para garantir que o usuário digite apenas números e no máximo 11 dígitos (DDD + Número)
        binding.editTextNumber.filters = arrayOf(InputFilter.LengthFilter(11))

        binding.btnVoltar.setOnClickListener { finish() }

        // Inicializa o adapter
        adapter = NumbersAdapter(
            numbersList,
            onClick = { selectedContact -> onNumberSelected(selectedContact.number) },
            onDelete = { contactToDelete -> confirmDelete(contactToDelete) }
        )
        binding.recyclerViewNumbers.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewNumbers.adapter = adapter

        // Listener para o botão de salvar número
        binding.btnSave.setOnClickListener {
            val phoneNumber = binding.editTextNumber.text.toString().trim()
            if (isValidPhoneNumber(phoneNumber)) {
                // Adiciona o código do país (55) antes de salvar
                saveNewNumber("55$phoneNumber")
            } else {
                Toast.makeText(this, "Digite um número válido com 11 dígitos (DDD + número).", Toast.LENGTH_SHORT).show()
            }
        }

        // Listener para o botão de salvar palavra-chave
        binding.btnSaveKeyword.setOnClickListener {
            val keyword = binding.editTextKeyword.text.toString().trim()
            if (keyword.isNotEmpty()) {
                saveUserField("voice_keyword", keyword, "Palavra-chave salva!")
            } else {
                Toast.makeText(this, "A palavra-chave não pode ser vazia.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun isValidPhoneNumber(number: String): Boolean {
        // Validação simples: 11 dígitos numéricos
        return number.length == 11 && number.all { it.isDigit() }
    }

    private fun loadUserData() {
        val userDocRef = db.collection("usuarios").document(auth.currentUser!!.uid)

        // Usamos addSnapshotListener para ouvir mudanças em tempo real
        firestoreListener = userDocRef.addSnapshotListener { snapshot, e ->
            if (e != null) {
                Toast.makeText(this, "Erro ao carregar dados do usuário.", Toast.LENGTH_SHORT).show()
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                val activeNumber = snapshot.getString("active_number")
                binding.textNum.text = if (activeNumber != null) "Número ativo: ${formatPhoneNumber(activeNumber)}" else "Número ativo: Nenhum"

                val activeKeyword = snapshot.getString("voice_keyword") ?: "ajuda"
                binding.textViewActiveKeyword.text = "Ativo: $activeKeyword"
                binding.editTextKeyword.setText(activeKeyword) // Preenche o campo de edição
            }
        }

        // Carrega a lista de contatos da subcoleção "numeros"
        userDocRef.collection("numeros").addSnapshotListener { snapshots, e ->
            if (e != null) {
                Toast.makeText(this, "Erro ao carregar contatos.", Toast.LENGTH_SHORT).show()
                return@addSnapshotListener
            }

            numbersList.clear()
            for (doc in snapshots!!) {
                numbersList.add(EmergencyContact(doc.id, doc.getString("number") ?: ""))
            }
            adapter.notifyDataSetChanged()
        }
    }

    private fun saveNewNumber(fullNumber: String) {
        if (numbersList.size >= 5) {
            Toast.makeText(this, "Limite de 5 números atingido.", Toast.LENGTH_SHORT).show()
            return
        }
        if (numbersList.any { it.number == fullNumber }) {
            Toast.makeText(this, "Número já cadastrado.", Toast.LENGTH_SHORT).show()
            return
        }

        val userContactsCollection = db.collection("usuarios").document(auth.currentUser!!.uid).collection("numeros")
        userContactsCollection.add(mapOf("number" to fullNumber))
            .addOnSuccessListener {
                binding.editTextNumber.text.clear()
                Toast.makeText(this, "Número salvo com sucesso!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Erro ao salvar número.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun confirmDelete(contactToDelete: EmergencyContact) {
        AlertDialog.Builder(this)
            .setTitle("Confirmar Exclusão")
            .setMessage("Deseja apagar o número ${formatPhoneNumber(contactToDelete.number)}?")
            .setPositiveButton("Sim") { _, _ -> deleteNumber(contactToDelete) }
            .setNegativeButton("Não", null)
            .show()
    }

    private fun deleteNumber(contactToDelete: EmergencyContact) {
        val userDocRef = db.collection("usuarios").document(auth.currentUser!!.uid)

        // Apaga o documento na subcoleção "numeros" usando o ID do contato
        userDocRef.collection("numeros").document(contactToDelete.id).delete()
            .addOnFailureListener {
                Toast.makeText(this, "Erro ao deletar o número.", Toast.LENGTH_SHORT).show()
            }

        // Verifica se o número apagado era o ativo e o remove se for o caso
        userDocRef.get().addOnSuccessListener {
            if (it.getString("active_number") == contactToDelete.number) {
                // Remove o campo "active_number" do documento do usuário
                userDocRef.update("active_number", FieldValue.delete())
            }
        }
    }

    private fun onNumberSelected(selectedNumber: String) {
        saveUserField("active_number", selectedNumber, "Número definido como ativo!")
    }

    private fun saveUserField(field: String, value: Any, successMessage: String) {
        val userDocRef = db.collection("usuarios").document(auth.currentUser!!.uid)
        // SetOptions.merge() garante que outros campos no documento não sejam sobrescritos
        userDocRef.set(mapOf(field to value), SetOptions.merge())
            .addOnSuccessListener {
                Toast.makeText(this, successMessage, Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Erro ao salvar.", Toast.LENGTH_SHORT).show()
            }
    }

    // Função de formatação de telefone centralizada
    private fun formatPhoneNumber(number: String): String {
        // Formato esperado: "5511987654321" (13 dígitos)
        return if (number.length == 13 && number.startsWith("55")) {
            val ddd = number.substring(2, 4)
            val firstPart = number.substring(4, 9) // "98765"
            val secondPart = number.substring(9)   // "4321"
            "($ddd) $firstPart-$secondPart"
        } else {
            number // Retorna o número original se não estiver no formato esperado
        }
    }
}


// --- Adapter Modernizado com View Binding ---

class NumbersAdapter(
    private val numbers: List<EmergencyContact>,
    private val onClick: (EmergencyContact) -> Unit,
    private val onDelete: (EmergencyContact) -> Unit
) : RecyclerView.Adapter<NumbersAdapter.NumberViewHolder>() {

    // ViewHolder interno que usa View Binding
    inner class NumberViewHolder(private val binding: ListItemNumberBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(contact: EmergencyContact) {
            // Acessa as views diretamente pelo binding
            binding.textNumber.text = formatPhoneNumber(contact.number)
            binding.root.setOnClickListener { onClick(contact) }
            binding.btnDelete.setOnClickListener { onDelete(contact) }
        }

        // Função de formatação para ser usada dentro do adapter
        private fun formatPhoneNumber(number: String): String {
            return if (number.length == 13 && number.startsWith("55")) {
                val ddd = number.substring(2, 4)
                val firstPart = number.substring(4, 9)
                val secondPart = number.substring(9)
                "($ddd) $firstPart-$secondPart"
            } else {
                number
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NumberViewHolder {
        // Infla o layout usando o binding
        val binding = ListItemNumberBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NumberViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NumberViewHolder, position: Int) {
        holder.bind(numbers[position])
    }

    override fun getItemCount(): Int = numbers.size
}