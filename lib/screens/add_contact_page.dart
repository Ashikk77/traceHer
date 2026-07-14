import 'package:flutter/material.dart';
import 'package:flutter_contacts/flutter_contacts.dart';
import 'package:permission_handler/permission_handler.dart';
import '../models/emergency_contact.dart';
import '../services/contact_service.dart';

class AddContactPage extends StatefulWidget {
  final EmergencyContact? contact;
  final int? index;

  const AddContactPage({
    super.key,
    this.contact,
    this.index,
  });

  @override
  State<AddContactPage> createState() => _AddContactPageState();
}

class _AddContactPageState extends State<AddContactPage> {
  final _formKey = GlobalKey<FormState>();

  final TextEditingController nameController = TextEditingController();
  final TextEditingController phoneController = TextEditingController();

  final ContactService contactService = ContactService();

  String relationship = "Mother";

  final List<String> relationships = [
    "Mother",
    "Father",
    "Brother",
    "Sister",
    "Friend",
    "Guardian",
    "Spouse",
    "Other",
  ];

  @override
  void initState() {
    super.initState();

    if (widget.contact != null) {
      nameController.text = widget.contact!.name;
      phoneController.text = widget.contact!.phoneNumber;
      relationship = widget.contact!.relationship;
    }
  }

  Future<void> pickContact() async {
    var status = await Permission.contacts.request();

    print("Permission Status: $status");

    if (!status.isGranted) {
      if (!mounted) return;

      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text("Contacts permission denied"),
        ),
      );
      return;
    }

    final Contact? contact = await FlutterContacts.openExternalPick();

    if (contact == null) return;

    if (contact.phones.isEmpty) {
      if (!mounted) return;

      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text("Selected contact has no phone number"),
        ),
      );
      return;
    }

    setState(() {
      nameController.text = contact.displayName;
      phoneController.text = contact.phones.first.number.replaceAll(
        RegExp(r'[^0-9+]'),
        '',
      );
    });
  }

  Future<void> saveContact() async {
    if (!_formKey.currentState!.validate()) return;

    final contact = EmergencyContact(
      name: nameController.text.trim(),
      phoneNumber: phoneController.text.trim(),
      relationship: relationship,
    );

    if (widget.contact == null) {
      bool success = await contactService.addContact(contact);

      if (!mounted) return;

      if (!success) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text("Maximum of 5 emergency contacts allowed."),
          ),
        );
        return;
      }
    } else {
      await contactService.updateContact(
        widget.index!,
        contact,
      );
    }

    if (!mounted) return;

    Navigator.pop(context, true);
  }

  @override
  void dispose() {
    nameController.dispose();
    phoneController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(
          widget.contact == null
              ? "Add Emergency Contact"
              : "Edit Emergency Contact",
        ),
        centerTitle: true,
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Form(
          key: _formKey,
          child: Column(
            children: [
              SizedBox(
                width: double.infinity,
                height: 50,
                child: OutlinedButton.icon(
                  onPressed: pickContact,
                  icon: const Icon(Icons.contacts),
                  label: const Text("Import From Phone Contacts"),
                ),
              ),

              const SizedBox(height: 20),

              TextFormField(
                controller: nameController,
                decoration: const InputDecoration(
                  labelText: "Name",
                  prefixIcon: Icon(Icons.person),
                  border: OutlineInputBorder(),
                ),
                validator: (value) {
                  if (value == null || value.trim().isEmpty) {
                    return "Enter a name";
                  }
                  return null;
                },
              ),

              const SizedBox(height: 16),

              TextFormField(
                controller: phoneController,
                keyboardType: TextInputType.phone,
                decoration: const InputDecoration(
                  labelText: "Phone Number",
                  prefixIcon: Icon(Icons.phone),
                  border: OutlineInputBorder(),
                ),
                validator: (value) {
                  if (value == null || value.trim().isEmpty) {
                    return "Enter a phone number";
                  }

                  if (value.trim().length < 10) {
                    return "Enter a valid phone number";
                  }

                  return null;
                },
              ),

              const SizedBox(height: 16),

              DropdownButtonFormField<String>(
                initialValue: relationship,
                decoration: const InputDecoration(
                  labelText: "Relationship",
                  border: OutlineInputBorder(),
                ),
                items: relationships.map((item) {
                  return DropdownMenuItem<String>(
                    value: item,
                    child: Text(item),
                  );
                }).toList(),
                onChanged: (value) {
                  setState(() {
                    relationship = value!;
                  });
                },
              ),

              const SizedBox(height: 30),

              SizedBox(
                width: double.infinity,
                height: 50,
                child: ElevatedButton.icon(
                  onPressed: saveContact,
                  icon: const Icon(Icons.save),
                  label: Text(
                    widget.contact == null
                        ? "Save Contact"
                        : "Update Contact",
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}