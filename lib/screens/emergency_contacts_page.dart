import 'package:flutter/material.dart';

import 'add_contact_page.dart';
import '../models/emergency_contact.dart';
import '../services/contact_service.dart';

class EmergencyContactsPage extends StatefulWidget {
  const EmergencyContactsPage({super.key});

  @override
  State<EmergencyContactsPage> createState() =>
      _EmergencyContactsPageState();
}

class _EmergencyContactsPageState
    extends State<EmergencyContactsPage> {
  final ContactService contactService = ContactService();

  List<EmergencyContact> contacts = [];

  @override
  void initState() {
    super.initState();
    loadContacts();
  }

  Future<void> loadContacts() async {
    contacts = await contactService.getContacts();

    if (mounted) {
      setState(() {});
    }
  }

  Future<void> deleteContact(int index) async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (context) {
        return AlertDialog(
          title: const Text("Delete Contact"),
          content: Text(
            "Are you sure you want to delete ${contacts[index].name}?",
          ),
          actions: [
            TextButton(
              onPressed: () {
                Navigator.pop(context, false);
              },
              child: const Text("Cancel"),
            ),
            ElevatedButton(
              onPressed: () {
                Navigator.pop(context, true);
              },
              child: const Text("Delete"),
            ),
          ],
        );
      },
    );

    if (confirm != true) return;

    await contactService.deleteContact(index);
    await loadContacts();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text("Emergency Contacts"),
        centerTitle: true,
      ),

      floatingActionButton: FloatingActionButton(
        child: const Icon(Icons.add),
        onPressed: () async {
          final result = await Navigator.push(
            context,
            MaterialPageRoute(
              builder: (_) => const AddContactPage(),
            ),
          );

          if (result == true) {
            await loadContacts();
          }
        },
      ),

      body: contacts.isEmpty
          ? const Center(
        child: Text(
          "No Emergency Contacts Added",
          style: TextStyle(fontSize: 18),
        ),
      )
          : ListView.builder(
        itemCount: contacts.length,
        itemBuilder: (context, index) {
          final contact = contacts[index];

          return Card(
            margin: const EdgeInsets.symmetric(
              horizontal: 12,
              vertical: 6,
            ),
            elevation: 3,
            child: ListTile(
              leading: const CircleAvatar(
                child: Icon(Icons.person),
              ),

              title: Text(
                contact.name,
                style: const TextStyle(
                  fontWeight: FontWeight.bold,
                ),
              ),

              subtitle: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(contact.relationship),
                  const SizedBox(height: 4),
                  Text(contact.phoneNumber),
                ],
              ),

              onTap: () async {
                final result = await Navigator.push(
                  context,
                  MaterialPageRoute(
                    builder: (_) => AddContactPage(
                      contact: contact,
                      index: index,
                    ),
                  ),
                );

                if (result == true) {
                  await loadContacts();
                }
              },

              trailing: IconButton(
                icon: const Icon(
                  Icons.delete,
                  color: Colors.red,
                ),
                onPressed: () async {
                  await deleteContact(index);
                },
              ),
            ),
          );
        },
      ),
    );
  }
}